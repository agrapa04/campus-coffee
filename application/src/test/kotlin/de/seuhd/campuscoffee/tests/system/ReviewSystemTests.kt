package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.ADMIN
import de.seuhd.campuscoffee.tests.SystemTestUtils.ADMIN_NO_MOD
import de.seuhd.campuscoffee.tests.SystemTestUtils.Credentials
import de.seuhd.campuscoffee.tests.SystemTestUtils.MODERATOR
import de.seuhd.campuscoffee.tests.SystemTestUtils.USER
import de.seuhd.campuscoffee.tests.SystemTestUtils.reviewRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * System tests for the operations related to reviews, including the approval workflow.
 * The default approval quorum is `campus-coffee.approval.min-count = 3`, so a review needs three
 * approvals from users other than the author to become approved. Every write request authenticates: the author
 * acts when creating/updating their review, and each approver acts under their own credentials so the
 * recorded approver is the caller.
 */
class ReviewSystemTests : AbstractSystemTest() {
    @Autowired
    private lateinit var approvalConfiguration: ApprovalConfiguration

    @Test
    fun `creating a review returns it unapproved`() {
        val pos = createPos()
        val (author, authorCredentials) = createUser("author", "author@uni-heidelberg.de")

        val created =
            reviewRequests
                .create(listOf(reviewFor(pos, "Solid espresso and plenty of seating.")), authorCredentials)
                .first()

        assertThat(created.approved).isFalse()
        // the author is the authenticated caller, not a request field
        assertThat(created.authorId).isEqualTo(author.id)
    }

    @Test
    fun `creating a review attributes it to the caller and rejects a body carrying an authorId`() {
        val pos = createPos()
        val (author, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val (other, _) = createUser("other", "other@uni-heidelberg.de")

        // a body that names a different author is rejected (400), like a client-supplied id
        val withAuthorId = reviewFor(pos, "Trying to post as someone else.").copy(authorId = other.id)
        assertThat(reviewRequests.createAndReturnStatusCodes(listOf(withAuthorId), authorCredentials).first())
            .isEqualTo(HttpStatus.BAD_REQUEST.value())

        // without the authorId, the review is attributed to the caller
        val created =
            reviewRequests.create(listOf(reviewFor(pos, "A genuine review by the caller.")), authorCredentials).first()
        assertThat(created.authorId).isEqualTo(author.id)
    }

    @Test
    fun `an unauthenticated write request returns 401 while a valid one returns 2xx`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")

        // no credentials: the chain rejects the write request with 401 before it reaches the controller
        val unauthenticated =
            reviewRequests.createUnauthenticatedAndReturnStatusCode(reviewFor(pos, "An anonymous review attempt."))
        assertThat(unauthenticated).isEqualTo(HttpStatus.UNAUTHORIZED.value())

        // the same request with valid credentials succeeds
        val created = reviewRequests.create(listOf(reviewFor(pos, "An authenticated review.")), authorCredentials)
        assertThat(created).hasSize(1)
    }

    @Test
    fun `the author may update and delete their review while a non-author gets 403`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val (_, strangerCredentials) = createUser("stranger", "stranger@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "The author's review, long enough.")),
                    authorCredentials
                ).first()

        // a non-author update and delete are forbidden
        assertThat(
            reviewRequests
                .updateAndReturnStatusCodes(
                    listOf(created.copy(review = "Hijacked text, long enough.")),
                    strangerCredentials
                ).first()
        ).isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(reviewRequests.deleteAndReturnStatusCodes(listOf(created.id!!), strangerCredentials).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())

        // the author may update
        val updated =
            reviewRequests
                .update(
                    listOf(created.copy(review = "Updated by the author, long enough.")),
                    authorCredentials
                ).first()
        assertThat(updated.review).isEqualTo("Updated by the author, long enough.")

        // and delete
        assertThat(reviewRequests.deleteAndReturnStatusCodes(listOf(created.id!!), authorCredentials).first())
            .isEqualTo(HttpStatus.NO_CONTENT.value())
    }

    @Test
    fun `a moderator may delete a foreign review while a plain USER cannot`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "A review others may try to delete.")),
                    authorCredentials
                ).first()

        // a plain USER who is not the author cannot delete it
        assertThat(reviewRequests.deleteAndReturnStatusCodes(listOf(created.id!!), USER).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())

        // a moderator may delete any review (204)
        assertThat(reviewRequests.deleteAndReturnStatusCodes(listOf(created.id!!), MODERATOR).first())
            .isEqualTo(HttpStatus.NO_CONTENT.value())
    }

    @Test
    fun `a moderator may update a foreign review while a plain USER cannot`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "A review others may try to edit.")),
                    authorCredentials
                ).first()

        // a plain USER who is not the author cannot edit it
        assertThat(
            reviewRequests
                .updateAndReturnStatusCodes(
                    listOf(created.copy(review = "Hijacked by a plain user, long enough.")),
                    USER
                ).first()
        ).isEqualTo(HttpStatus.FORBIDDEN.value())

        // a moderator may edit any review (200); the author stays the original
        val moderated =
            reviewRequests
                .update(
                    listOf(created.copy(review = "Tidied up by a moderator, long enough.")),
                    MODERATOR
                ).first()
        assertThat(moderated.review).isEqualTo("Tidied up by a moderator, long enough.")
        assertThat(moderated.authorId).isEqualTo(created.authorId)
    }

    @Test
    fun `an admin who is also a moderator may edit and delete a foreign review`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(listOf(reviewFor(pos, "A review an admin-moderator will touch.")), authorCredentials)
                .first()

        // the ADMIN fixture also holds MODERATOR, so it may edit any review (200, author preserved)
        val edited =
            reviewRequests
                .update(listOf(created.copy(review = "Edited by an admin-moderator, long enough.")), ADMIN)
                .first()
        assertThat(edited.review).isEqualTo("Edited by an admin-moderator, long enough.")
        assertThat(edited.authorId).isEqualTo(created.authorId)

        assertThat(reviewRequests.deleteAndReturnStatusCodes(listOf(created.id!!), ADMIN).first())
            .isEqualTo(HttpStatus.NO_CONTENT.value())
    }

    @Test
    fun `an admin who is not a moderator cannot edit or delete a foreign review`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(listOf(reviewFor(pos, "A review for the admin orthogonality check.")), authorCredentials)
                .first()

        // review moderation is gated on MODERATOR, not ADMIN, so an admin without MODERATOR gets 403
        val edit = created.copy(review = "Admin without moderator trying to edit, long enough.")
        assertThat(reviewRequests.updateAndReturnStatusCodes(listOf(edit), ADMIN_NO_MOD).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(reviewRequests.deleteAndReturnStatusCodes(listOf(created.id!!), ADMIN_NO_MOD).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun `listing reviews and fetching one by id return the created review`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "A reliable spot between lectures.")),
                    authorCredentials
                ).first()

        assertThat(reviewRequests.retrieveAll().map { it.id }).containsExactly(created.id)

        val byId = reviewRequests.retrieveById(created.id!!)
        assertThat(byId.review).isEqualTo("A reliable spot between lectures.")
    }

    @Test
    fun `updating a review changes its text`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "Original review text, long enough.")),
                    authorCredentials
                ).first()

        val updated =
            reviewRequests
                .update(listOf(created.copy(review = "Updated review text, also long enough.")), authorCredentials)
                .first()

        assertThat(updated.review).isEqualTo("Updated review text, also long enough.")
        assertThat(reviewRequests.retrieveById(created.id!!).review)
            .isEqualTo("Updated review text, also long enough.")
    }

    @Test
    fun `updating a review keeps its approval state`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        // exactly the configured quorum of distinct approvers, so the test does not depend on min-count = 3
        val approvers =
            (1..approvalConfiguration.minCount)
                .map { createUser("approver_$it", "approver$it@uni-heidelberg.de") }
        val created =
            reviewRequests.create(listOf(reviewFor(pos, "Review text before the update.")), authorCredentials).first()
        approvers.forEach { (_, credentials) -> reviewRequests.approve(created.id!!, credentials) }
        assertThat(reviewRequests.retrieveById(created.id!!).approved).isTrue()

        // approvals are managed by the approval workflow; a text edit must not erase them
        val updated =
            reviewRequests
                .update(listOf(created.copy(review = "Review text after the update.")), authorCredentials)
                .first()

        assertThat(updated.review).isEqualTo("Review text after the update.")
        assertThat(updated.approved).isTrue()
        assertThat(reviewRequests.retrieveById(created.id!!).approved).isTrue()
    }

    @Test
    fun `re-pointing a review at a different POS returns 400 Bad Request`() {
        val firstPos = createPos()
        val secondPos =
            posService.upsert(
                TestFixtures.getPosFixturesForInsertion().last().copy(name = "Second POS for the move test")
            )
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val reviewOnFirstPos =
            reviewRequests.create(listOf(reviewFor(firstPos, "Review for the first POS.")), authorCredentials).first()
        val reviewOnSecondPos =
            reviewRequests.create(listOf(reviewFor(secondPos, "Review for the second POS.")), authorCredentials).first()

        // a review's POS and author are fixed at creation: re-pointing the second review at the first
        // POS would yield two reviews by the same author and carry approvals to the wrong POS
        val movedReview = reviewOnSecondPos.copy(posId = firstPos.id)
        val statusCode =
            reviewRequests.updateAndReturnStatusCodes(listOf(movedReview), authorCredentials).first()

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value())
        // both reviews still point at their original POS
        assertThat(reviewRequests.retrieveById(reviewOnFirstPos.id!!).posId).isEqualTo(firstPos.id)
        assertThat(reviewRequests.retrieveById(reviewOnSecondPos.id!!).posId).isEqualTo(secondPos.id)
    }

    @Test
    fun `updating a review that does not exist returns 404 Not Found`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val existing =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "The author's only review, long enough.")),
                    authorCredentials
                ).first()

        // the existence check runs before the author/role check, so a missing review is 404 (not a 403)
        // even for a plain non-author user
        val ghost = existing.copy(id = MISSING_ID)
        val statusCode = reviewRequests.updateAndReturnStatusCodes(listOf(ghost), USER).first()

        assertThat(statusCode).isEqualTo(HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `deleting a review twice returns 204 No Content then 404 Not Found`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests.create(listOf(reviewFor(pos, "This review will be deleted.")), authorCredentials).first()
        val id = requireNotNull(created.id)

        val statusCodes = reviewRequests.deleteAndReturnStatusCodes(listOf(id, id), authorCredentials)

        // the first delete returns 204 No Content, the second 404 Not Found
        assertThat(statusCodes).containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `approving a review below the quorum leaves it unapproved`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val (_, firstApprover) = createUser("approver_one", "approver.one@uni-heidelberg.de")
        val (_, secondApprover) = createUser("approver_two", "approver.two@uni-heidelberg.de")
        val review =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "Review that stays below the quorum.")),
                    authorCredentials
                ).first()

        reviewRequests.approve(review.id!!, firstApprover)
        val afterTwoApprovals = reviewRequests.approve(review.id!!, secondApprover)

        assertThat(afterTwoApprovals.approved).isFalse()
    }

    @Test
    fun `approving a review up to the quorum marks it approved`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val (_, firstApprover) = createUser("approver_one", "approver.one@uni-heidelberg.de")
        val (_, secondApprover) = createUser("approver_two", "approver.two@uni-heidelberg.de")
        val (_, thirdApprover) = createUser("approver_three", "approver.three@uni-heidelberg.de")
        val review =
            reviewRequests.create(listOf(reviewFor(pos, "Review that reaches the quorum.")), authorCredentials).first()

        reviewRequests.approve(review.id!!, firstApprover)
        reviewRequests.approve(review.id!!, secondApprover)
        val afterThreeApprovals = reviewRequests.approve(review.id!!, thirdApprover)

        assertThat(afterThreeApprovals.approved).isTrue()
    }

    @Test
    fun `approving your own review returns 400 Bad Request`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val review =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "Author tries to approve this review.")),
                    authorCredentials
                ).first()

        // the author approves their own review; the approver is the authenticated user, so no id is sent
        val statusCode = reviewRequests.approveAndReturnStatusCode(review.id!!, authorCredentials)

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `the same user approving a review twice returns 409 Conflict`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        val (_, approverCredentials) = createUser("approver", "approver@uni-heidelberg.de")
        val review =
            reviewRequests
                .create(
                    listOf(reviewFor(pos, "A review approved once then again.")),
                    authorCredentials
                ).first()

        // the first approval is recorded; the second by the same user hits the unique constraint -> 409
        reviewRequests.approve(review.id!!, approverCredentials)
        val secondStatus = reviewRequests.approveAndReturnStatusCode(review.id!!, approverCredentials)

        assertThat(secondStatus).isEqualTo(HttpStatus.CONFLICT.value())
        // the repeated approval did not inflate the count
        assertThat(reviewRequests.retrieveById(review.id!!).approved).isFalse()
    }

    @Test
    fun `creating a second review by the same author for a POS returns 409 Conflict`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("author", "author@uni-heidelberg.de")
        reviewRequests.create(listOf(reviewFor(pos, "First review by this author.")), authorCredentials)

        // a duplicate is a conflict (the same status the uq_reviews_pos_author constraint produces
        // when two concurrent creates race past the application-level check)
        val statusCode =
            reviewRequests
                .createAndReturnStatusCodes(
                    listOf(reviewFor(pos, "Second review by the same author.")),
                    authorCredentials
                ).first()

        assertThat(statusCode).isEqualTo(HttpStatus.CONFLICT.value())
    }

    @Test
    fun `approving a missing review returns 404 Not Found`() {
        val (_, approverCredentials) = createUser("approver", "approver@uni-heidelberg.de")

        val statusCode = reviewRequests.approveAndReturnStatusCode(MISSING_ID, approverCredentials)

        assertThat(statusCode).isEqualTo(HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `filtering reviews by approval status returns only the matching reviews`() {
        val pos = createPos()
        val (_, approvedAuthor) = createUser("approved_author", "approved.author@uni-heidelberg.de")
        val (_, pendingAuthor) = createUser("pending_author", "pending.author@uni-heidelberg.de")
        val (_, firstApprover) = createUser("approver_one", "approver.one@uni-heidelberg.de")
        val (_, secondApprover) = createUser("approver_two", "approver.two@uni-heidelberg.de")
        val (_, thirdApprover) = createUser("approver_three", "approver.three@uni-heidelberg.de")

        val approvedReview =
            reviewRequests.create(listOf(reviewFor(pos, "This review reaches the quorum.")), approvedAuthor).first()
        val pendingReview =
            reviewRequests.create(listOf(reviewFor(pos, "This review stays below the quorum.")), pendingAuthor).first()

        reviewRequests.approve(approvedReview.id!!, firstApprover)
        reviewRequests.approve(approvedReview.id!!, secondApprover)
        reviewRequests.approve(approvedReview.id!!, thirdApprover)

        val posId = requireNotNull(pos.id)
        val approved = reviewRequests.retrieveByFilter(mapOf("pos_id" to posId, "approved" to true))
        assertThat(approved.map { it.id }).containsExactly(approvedReview.id)

        val pending = reviewRequests.retrieveByFilter(mapOf("pos_id" to posId, "approved" to false))
        assertThat(pending.map { it.id }).containsExactly(pendingReview.id)
    }

    // helpers ---------------------------------------------------------------------

    private fun createPos(): Pos = posService.upsert(TestFixtures.getPosFixturesForInsertion().first())

    /**
     * Creates a user with a known password (so it can authenticate over HTTP Basic) and returns it
     * together with its credentials.
     */
    private fun createUser(
        loginName: String,
        emailAddress: String
    ): Pair<User, Credentials> {
        val password = "test-password-$loginName"
        val user =
            userService.upsert(
                User(
                    loginName = loginName,
                    emailAddress = emailAddress,
                    firstName = "First",
                    lastName = "Last",
                    password = password
                )
            )
        return user to Credentials(loginName, password)
    }

    private fun reviewFor(
        pos: Pos,
        text: String
    ): ReviewDto = ReviewDto(posId = pos.id, review = text)

    private companion object {
        // an id no review carries (well beyond the deterministic test generator's range)
        val MISSING_ID: UUID = UUID(0L, 1_000_000L)
    }
}
