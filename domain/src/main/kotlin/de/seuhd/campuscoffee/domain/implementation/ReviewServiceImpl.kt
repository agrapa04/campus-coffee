package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.configuration.ApprovalProperties
import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.exceptions.ForbiddenException
import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.model.objects.Role
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.model.objects.persistedId
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Implementation of the Review service that handles business logic related to review entities.
 */
@Service
class ReviewServiceImpl(
    private val reviewDataService: ReviewDataService,
    private val userDataService: UserDataService,
    private val posDataService: PosDataService,
    private val reviewApprovalDataService: ReviewApprovalDataService,
    private val approvalProperties: ApprovalProperties
) : CrudServiceImpl<Review, UUID>(Review::class.java),
    ReviewService {
    override fun dataService(): CrudDataService<Review, UUID> = reviewDataService

    @Transactional
    override fun upsert(domainObject: Review): Review {
        // validate that the POS exists before creating or updating the review
        val pos = posDataService.getById(domainObject.pos.persistedId)

        val reviewToUpsert =
            domainObject.id?.let { id ->
                // loading the existing review first makes an update of a missing review a 404
                val existingReview = reviewDataService.getById(id)

                // a review's POS and author are fixed at creation: re-pointing it would bypass the
                // one-review-per-author-per-POS rule and carry approvals to a POS nobody approved
                // for, and changing the author could mark a review as approved by its own author
                if (existingReview.pos.id != pos.id || existingReview.author.id != domainObject.author.id) {
                    throw ValidationException(
                        "The POS and author of review with ID '$id' cannot be changed."
                    )
                }

                // approvals are managed by the approval workflow (see approve); an update keeps the
                // existing approval state instead of accepting it from the caller
                domainObject.copy(
                    approvalCount = existingReview.approvalCount,
                    approved = existingReview.approved
                )
            } ?: domainObject

        // an author may review a POS only once (updates cannot change the pair, so only creation can
        // violate the rule); the uq_reviews_pos_author database constraint is the authoritative guard
        // that also closes the concurrent-create race, reported with the same 409 as this check
        if (domainObject.id == null && reviewDataService.filter(pos, domainObject.author).isNotEmpty()) {
            throw DuplicationException(
                Review::class.java,
                "pos_id/author_id",
                "POS ${pos.id}, author ${domainObject.author.id}"
            )
        }

        return super.upsert(reviewToUpsert)
    }

    @Transactional
    override fun create(
        review: Review,
        actingUser: User
    ): Review =
        // the author is the authenticated user, never a client-supplied field; the api layer has already
        // rejected a body carrying an authorId before reaching here
        upsert(review.copy(author = actingUser))

    @Transactional
    override fun update(
        review: Review,
        actingUser: User
    ): Review {
        val reviewId = requireNotNull(review.id) { "A review update must carry the review id." }
        val existing = reviewDataService.getById(reviewId)
        requireAuthorOrModerator(existing, actingUser, "update")
        // the author never changes on update; pin it to the original so a moderator editing someone
        // else's review does not re-author it (and upsert's author-immutability check stays satisfied)
        return upsert(review.copy(author = existing.author))
    }

    @Transactional
    override fun delete(
        reviewId: UUID,
        actingUser: User
    ) {
        val existing = reviewDataService.getById(reviewId)
        requireAuthorOrModerator(existing, actingUser, "delete")
        super.delete(reviewId)
    }

    /**
     * Requires that [actingUser] is the review's author or a moderator before an edit or delete; ownership
     * and role combine here, on the domain User (gated on MODERATOR, not ADMIN).
     */
    private fun requireAuthorOrModerator(
        existing: Review,
        actingUser: User,
        action: String
    ) {
        val isAuthor = existing.author.persistedId == actingUser.persistedId
        val isModerator = Role.MODERATOR in actingUser.roles
        if (!isAuthor && !isModerator) {
            throw ForbiddenException(
                "Only the author or a moderator may $action review with ID '${existing.persistedId}'."
            )
        }
    }

    @Transactional(readOnly = true)
    override fun filter(
        posId: UUID,
        approved: Boolean
    ): List<Review> = reviewDataService.filter(posDataService.getById(posId), approved)

    @Transactional
    override fun approve(
        reviewId: UUID,
        actingUser: User
    ): Review {
        val approverId = actingUser.persistedId
        log.info { "Processing approval request for review with ID '$reviewId' by user with ID '$approverId'..." }

        // validate that the review exists
        val reviewToApprove = reviewDataService.getById(reviewId)
        val authorId = reviewToApprove.author.persistedId

        // a user cannot approve their own review (the approver is the authenticated user)
        if (authorId == approverId) {
            log.warn { "User with ID '$approverId' attempted to approve their own review with ID '$reviewId'." }
            throw ValidationException(
                "User with ID '$approverId' cannot approve their own review with ID '$reviewId'."
            )
        }

        // record who approved; the unique (review_id, user_id) constraint rejects a repeat as a 409
        // (DuplicationException via the registered ConstraintMapping), so the count can no longer be inflated
        reviewApprovalDataService.record(ReviewApproval(reviewId = reviewId, userId = approverId))

        // the approval state comes from the recorded rows: the number of distinct approvers, and
        // whether that count meets the quorum
        val approvalCount = reviewApprovalDataService.countByReviewId(reviewId)
        val approved = approvalCount >= approvalProperties.minCount
        val quorumProgress = "$approvalCount/${approvalProperties.minCount}"
        if (approved) {
            log.info { "Review with ID '$reviewId' has now reached the approval quorum ($quorumProgress)" }
        } else {
            log.info { "Review with ID '$reviewId' has not reached the approval quorum ($quorumProgress)" }
        }

        return reviewDataService.upsert(
            reviewToApprove.copy(approvalCount = approvalCount.toInt(), approved = approved)
        )
    }

    private companion object {
        private val log = KotlinLogging.logger {}
    }
}
