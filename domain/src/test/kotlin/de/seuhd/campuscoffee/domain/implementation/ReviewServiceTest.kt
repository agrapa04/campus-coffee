package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

/**
 * Unit and integration tests for the operations related to reviews.
 */
@ExtendWith(MockitoExtension::class)
class ReviewServiceTest {
    private val approvalConfiguration = TestFixtures.getApprovalConfiguration()

    @Mock
    private lateinit var reviewDataService: ReviewDataService

    @Mock
    private lateinit var userDataService: UserDataService

    @Mock
    private lateinit var posDataService: PosDataService

    private lateinit var reviewService: ReviewServiceImpl

    @BeforeEach
    fun beforeEach() {
        reviewService = ReviewServiceImpl(reviewDataService, userDataService, posDataService, approvalConfiguration)
    }

    @Test
    fun approvalFailsIfUserIsAuthor() {
        val review = TestFixtures.getReviewFixtures().first()
        val authorId = requireNotNull(review.author.id)
        whenever(userDataService.getById(authorId)).thenReturn(review.author)
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)

        assertThrows<ValidationException> { reviewService.approve(reviewId, authorId) }
        verify(userDataService).getById(authorId)
        verify(reviewDataService).getById(reviewId)
    }

    @Test
    fun approvalSuccessfulIfUserIsNotAuthor() {
        // one short of the quorum, so the single approval below pushes it to exactly the quorum
        val review =
            TestFixtures
                .getReviewFixtures()
                .first()
                .copy(approvalCount = approvalConfiguration.minCount!! - 1, approved = false)
        val user = TestFixtures.getUserFixtures().last()
        val userId = requireNotNull(user.id)
        whenever(userDataService.getById(userId)).thenReturn(user)
        val reviewId = requireNotNull(review.id)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)
        whenever(reviewDataService.upsert(any<Review>())).thenAnswer { it.getArgument<Review>(0) }

        val approvedReview = reviewService.approve(reviewId, userId)

        verify(userDataService).getById(userId)
        verify(reviewDataService).getById(reviewId)
        verify(reviewDataService).upsert(any<Review>())
        assertThat(approvedReview.approvalCount).isEqualTo(review.approvalCount + 1)
        assertThat(approvedReview.approved).isTrue()
    }

    @Test
    fun retrieveAllApprovedPos() {
        val pos = TestFixtures.getPosFixtures().first()
        val posId = requireNotNull(pos.id)
        val reviews =
            TestFixtures.getReviewFixtures().map {
                it.copy(pos = pos, approvalCount = approvalConfiguration.minCount!!, approved = true)
            }
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.filter(pos, true)).thenReturn(reviews)

        val retrievedReviews = reviewService.filter(posId, true)

        verify(posDataService).getById(posId)
        verify(reviewDataService).filter(pos, true)
        assertThat(retrievedReviews).hasSize(reviews.size)
    }

    @Test
    fun createReviewPosDoesNotExistException() {
        val review = TestFixtures.getReviewFixtures().first()
        val posId = requireNotNull(review.pos.id)
        whenever(posDataService.getById(posId)).thenThrow(NotFoundException(review.pos.javaClass, posId))

        assertThrows<NotFoundException> { reviewService.upsert(review) }
        verify(posDataService).getById(posId)
    }

    @Test
    fun userCannotCreateMoreThanOneReviewPerPos() {
        // given a new review (id is null), since the duplicate check runs only on creation
        val review = TestFixtures.getReviewFixturesForInsertion().first()
        val pos = review.pos
        val author = review.author
        val posId = requireNotNull(pos.id)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.filter(pos, author)).thenReturn(listOf(review))

        assertThrows<ValidationException> { reviewService.upsert(review) }
        verify(posDataService).getById(posId)
        verify(reviewDataService).filter(pos, author)
    }

    @Test
    fun approvalApprovesReviewWhenThresholdIsReached() {
        val quorum = approvalConfiguration.minCount!!
        val unapprovedReview =
            TestFixtures
                .getReviewFixtures()
                .first()
                .copy(approvalCount = quorum - 1, approved = false)

        var updatedReview = reviewService.updateApprovalStatus(unapprovedReview)
        assertFalse(updatedReview.approved)

        val approvedReview = unapprovedReview.copy(approvalCount = quorum)
        updatedReview = reviewService.updateApprovalStatus(approvedReview)
        assertTrue(updatedReview.approved)
    }

    @Test
    fun approvalDoesNotApproveReviewWhenThresholdIsNotReached() {
        val review = TestFixtures.getReviewFixtures().first().copy(approvalCount = 0, approved = false)
        val reviewId = requireNotNull(review.id)
        val user = TestFixtures.getUserFixtures().last()
        val userId = requireNotNull(user.id)
        whenever(userDataService.getById(userId)).thenReturn(user)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)
        whenever(reviewDataService.upsert(any<Review>())).thenAnswer { it.getArgument<Review>(0) }

        val approvedReview = reviewService.approve(reviewId, userId)

        verify(userDataService).getById(userId)
        verify(reviewDataService).getById(reviewId)
        verify(reviewDataService).upsert(any<Review>())
        assertThat(approvedReview.approvalCount).isEqualTo(1)
        assertThat(approvedReview.approved).isFalse()
    }

    @Test
    fun reviewCreationSuccessfulForExistingPosAndAuthorWithoutPreviousReview() {
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
    fun updatingExistingReviewSkipsDuplicateCheck() {
        // given an existing review with a non-null id
        val review = TestFixtures.getReviewFixtures().first()
        val pos = review.pos
        val reviewId = requireNotNull(review.id)
        val posId = requireNotNull(pos.id)
        whenever(posDataService.getById(posId)).thenReturn(pos)
        whenever(reviewDataService.getById(reviewId)).thenReturn(review)
        whenever(reviewDataService.upsert(review)).thenReturn(review)

        val result = reviewService.upsert(review)

        // the per-author filter is never consulted on update
        verify(reviewDataService, never()).filter(any<Pos>(), any<User>())
        verify(reviewDataService).upsert(review)
        assertThat(result.id).isEqualTo(review.id)
    }
}
