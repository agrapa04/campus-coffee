package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.exceptions.ForbiddenException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit and integration tests for the operations related to reviews.
 */
@ExtendWith(MockitoExtension::class)
class ReviewServiceTest {
    private val approvalConfiguration = TestFixtures.getApprovalConfiguration()

    // an id no fixture review carries, for the "update a missing review" case
    private val missingReviewId = UUID(0L, 9999L)

    @Mock
    private lateinit var reviewDataService: ReviewDataService

    @Mock
    private lateinit var userDataService: UserDataService

    @Mock
    private lateinit var posDataService: PosDataService

    @Mock
    private lateinit var reviewApprovalDataService: ReviewApprovalDataService

    private lateinit var reviewService: ReviewServiceImpl

    @BeforeEach
    fun beforeEach() {
        reviewService =
            ReviewServiceImpl(
                reviewDataService,
                userDataService,
                posDataService,
                reviewApprovalDataService,
                approvalConfiguration
            )
    }

    @Test
    fun `approve throws ValidationException when the user is the author`() {
        val review = TestFixtures.getReviewFixtures().first()
        val author = review.author
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)

        assertThrows<ValidationException> { reviewService.approve(reviewId, author) }
        verify(reviewDataService).getById(reviewId)
        // a self-approval is rejected before any approval row is recorded
        verify(reviewApprovalDataService, never()).record(any())
    }

    @Test
    fun `approve records the approver and marks the review approved at the quorum`() {
        val review = TestFixtures.getReviewFixtures().first()
        val approver = TestFixtures.getUserFixtures().first { it.id != review.author.id }
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)
        // the recorded approvals now meet the quorum, so the derived state is approved
        whenever(
            reviewApprovalDataService.countByReviewId(reviewId)
        ).thenReturn(approvalConfiguration.minCount.toLong())
        whenever(reviewDataService.upsert(any<Review>())).thenAnswer { it.getArgument<Review>(0) }

        val approvedReview = reviewService.approve(reviewId, approver)

        verify(reviewApprovalDataService).record(
            ReviewApproval(reviewId = reviewId, userId = requireNotNull(approver.id))
        )
        verify(reviewApprovalDataService).countByReviewId(reviewId)
        assertThat(approvedReview.approvalCount).isEqualTo(approvalConfiguration.minCount)
        assertThat(approvedReview.approved).isTrue()
    }

    @Test
    fun `approve records the approver but leaves the review unapproved below the quorum`() {
        val review = TestFixtures.getReviewFixtures().first()
        val approver = TestFixtures.getUserFixtures().first { it.id != review.author.id }
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)
        whenever(reviewApprovalDataService.countByReviewId(reviewId)).thenReturn(1L)
        whenever(reviewDataService.upsert(any<Review>())).thenAnswer { it.getArgument<Review>(0) }

        val approvedReview = reviewService.approve(reviewId, approver)

        verify(reviewApprovalDataService).record(any())
        assertThat(approvedReview.approvalCount).isEqualTo(1)
        assertThat(approvedReview.approved).isFalse()
    }

    @Test
    fun `approve propagates the DuplicationException when the same user approves twice`() {
        val review = TestFixtures.getReviewFixtures().first()
        val approver = TestFixtures.getUserFixtures().first { it.id != review.author.id }
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)
        // the unique (review_id, user_id) constraint, surfaced by the data layer, rejects the repeat
        whenever(reviewApprovalDataService.record(any())).thenThrow(
            DuplicationException(
                ReviewApproval::class.java,
                "review_id/user_id",
                "review $reviewId, user ${approver.id}"
            )
        )

        assertThrows<DuplicationException> { reviewService.approve(reviewId, approver) }
        // no derived state is written when the approval is rejected
        verify(reviewDataService, never()).upsert(any<Review>())
    }

    @Test
    fun `update throws ForbiddenException when the acting user is neither author nor moderator`() {
        val plainUser = TestFixtures.plainUser()
        val review = TestFixtures.getReviewFixtures().first { it.author.id != plainUser.id }
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)

        assertThrows<ForbiddenException> { reviewService.update(review, plainUser) }
        verify(reviewDataService, never()).upsert(any<Review>())
    }

    @Test
    fun `delete throws ForbiddenException when the acting user is neither author nor moderator`() {
        val plainUser = TestFixtures.plainUser()
        val review = TestFixtures.getReviewFixtures().first { it.author.id != plainUser.id }
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)

        assertThrows<ForbiddenException> { reviewService.delete(reviewId, plainUser) }
        verify(reviewDataService, never()).delete(any())
    }

    @Test
    fun `delete allows a moderator to delete a review they did not author`() {
        val moderator = TestFixtures.moderator()
        val review = TestFixtures.getReviewFixtures().first { it.author.id != moderator.id }
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)

        reviewService.delete(reviewId, moderator)

        verify(reviewDataService).delete(reviewId)
    }

    @Test
    fun `update allows a moderator to update a review they did not author`() {
        val moderator = TestFixtures.moderator()
        val existingReview = TestFixtures.getReviewFixtures().first { it.author.id != moderator.id }
        val pos = existingReview.pos
        val reviewId = requireNotNull(existingReview.id)
        val posId = requireNotNull(pos.id)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.getById(reviewId)).thenReturn(existingReview)
        whenever(reviewDataService.upsert(any<Review>())).thenAnswer { it.getArgument<Review>(0) }

        // the payload carries the moderator as its author (as the api mapper does); the pin must overwrite
        // it back to the original, so this assertion fails if the author pin is removed
        val update = existingReview.copy(review = "Moderated text, also long enough.", author = moderator)
        val result = reviewService.update(update, moderator)

        // the moderator's edit goes through, and the author stays the original (not re-authored)
        assertThat(result.review).isEqualTo("Moderated text, also long enough.")
        assertThat(result.author.id).isEqualTo(existingReview.author.id)
    }

    @Test
    fun `create attributes the review to the acting user`() {
        val author = TestFixtures.getUserFixtures().first()
        val review = TestFixtures.getReviewFixturesForInsertion().last()
        val pos = review.pos
        val posId = requireNotNull(pos.id)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.filter(pos, author)).thenReturn(listOf())
        whenever(reviewDataService.upsert(any<Review>())).thenAnswer { it.getArgument<Review>(0) }

        val created =
            reviewService.create(
                review.copy(
                    author =
                        TestFixtures.getUserFixtures().first {
                            it.id !=
                                author.id
                        }
                ),
                author
            )

        // the author is the acting user, not whatever the review carried
        assertThat(created.author).isEqualTo(author)
    }

    @Test
    fun `filter returns the approved reviews for a POS`() {
        val pos = TestFixtures.anyPos()
        val posId = requireNotNull(pos.id)
        val reviews =
            TestFixtures.getReviewFixtures().map {
                it.copy(pos = pos, approvalCount = approvalConfiguration.minCount, approved = true)
            }
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.filter(pos, true)).thenReturn(reviews)

        val retrievedReviews = reviewService.filter(posId, true)

        verify(posDataService).getById(posId)
        verify(reviewDataService).filter(pos, true)
        assertThat(retrievedReviews).hasSize(reviews.size)
    }

    @Test
    fun `upsert throws NotFoundException when the POS does not exist`() {
        val review = TestFixtures.getReviewFixtures().first()
        val posId = requireNotNull(review.pos.id)
        whenever(posDataService.getById(posId)).thenThrow(NotFoundException(review.pos.javaClass, posId))

        assertThrows<NotFoundException> { reviewService.upsert(review) }
        verify(posDataService).getById(posId)
    }

    @Test
    fun `upsert throws DuplicationException for a duplicate review by the same author and POS`() {
        // a new review (id is null) while a persisted review by the same author for the same POS
        // exists; a duplicate is a 409 conflict, matching the uq_reviews_pos_author database constraint
        val review = TestFixtures.getReviewFixturesForInsertion().first()
        val persistedReview = TestFixtures.getReviewFixtures().first()
        val pos = review.pos
        val author = review.author
        val posId = requireNotNull(pos.id)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.filter(pos, author)).thenReturn(listOf(persistedReview))

        assertThrows<DuplicationException> { reviewService.upsert(review) }
        verify(posDataService).getById(posId)
        verify(reviewDataService).filter(pos, author)
    }

    @Test
    fun `upsert saves a first review for an existing POS and author`() {
        val review = TestFixtures.getReviewFixturesForInsertion().first()
        val pos = review.pos
        val posId = requireNotNull(pos.id)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.filter(pos, review.author)).thenReturn(listOf())
        whenever(reviewDataService.upsert(review)).thenReturn(review)

        val result = reviewService.upsert(review)

        verify(posDataService).getById(posId)
        verify(reviewDataService).filter(pos, review.author)
        verify(reviewDataService).upsert(review)
        assertThat(result.id).isEqualTo(review.id)
    }

    @Test
    fun `upsert updates a review and keeps its approval state`() {
        // the persisted review is approved; the update carries reset values, which must not overwrite
        // the approval state managed by the approval workflow
        val existingReview = TestFixtures.getReviewFixtures().first().copy(approvalCount = 3, approved = true)
        val update =
            existingReview.copy(review = "Updated text for this review!", approvalCount = 0, approved = false)
        val pos = existingReview.pos
        val reviewId = requireNotNull(existingReview.id)
        val posId = requireNotNull(pos.id)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.getById(reviewId)).thenReturn(existingReview)
        whenever(reviewDataService.upsert(any<Review>())).thenAnswer { it.getArgument<Review>(0) }

        val result = reviewService.upsert(update)

        assertThat(result.review).isEqualTo(update.review)
        assertThat(result.approvalCount).isEqualTo(existingReview.approvalCount)
        assertThat(result.approved).isEqualTo(existingReview.approved)
    }

    @Test
    fun `upsert throws NotFoundException when updating a missing review`() {
        // the author already has a review for the POS, so a wrong order of checks would report the
        // duplicate conflict; the unknown id must win and yield a 404
        val persistedReview = TestFixtures.getReviewFixtures().first()
        val pos = persistedReview.pos
        val posId = requireNotNull(pos.id)
        val update = persistedReview.copy(id = missingReviewId)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.getById(missingReviewId))
            .thenThrow(NotFoundException(Review::class.java, missingReviewId))

        assertThrows<NotFoundException> { reviewService.upsert(update) }
        verify(reviewDataService, never()).upsert(any<Review>())
    }

    @Test
    fun `upsert throws ValidationException when an update re-points a review at a different POS`() {
        // the persisted review belongs to one POS; the update payload points at another. Moving a
        // review would carry its approvals to a POS nobody approved a review for.
        val persistedReview = TestFixtures.unapprovedReview()
        val targetPos = TestFixtures.getPosFixtures().first { it.id != persistedReview.pos.id }
        val update = persistedReview.copy(pos = targetPos)
        val reviewId = requireNotNull(persistedReview.id)
        whenever(posDataService.getById(requireNotNull(targetPos.id))).thenReturn(targetPos)
        whenever(reviewDataService.getById(reviewId)).thenReturn(persistedReview)

        assertThrows<ValidationException> { reviewService.upsert(update) }
        verify(reviewDataService, never()).upsert(any<Review>())
    }

    @Test
    fun `upsert throws ValidationException when an update changes the author of a review`() {
        // changing the author could retroactively mark a review as approved by its own author
        val persistedReview = TestFixtures.getReviewFixtures().first()
        val newAuthor = TestFixtures.getUserFixtures().first { it.id != persistedReview.author.id }
        val update = persistedReview.copy(author = newAuthor)
        val pos = persistedReview.pos
        val reviewId = requireNotNull(persistedReview.id)
        whenever(posDataService.getById(requireNotNull(pos.id))).thenReturn(pos)
        whenever(reviewDataService.getById(reviewId)).thenReturn(persistedReview)

        assertThrows<ValidationException> { reviewService.upsert(update) }
        verify(reviewDataService, never()).upsert(any<Review>())
    }
}
