package de.seuhd.campuscoffee.data.integration

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

/**
 * Integration tests for the review approval data service against a real PostgreSQL container: recording
 * approvals, counting them, the "one approval per user per review" uniqueness constraint mapped to a
 * [DuplicationException], and the clear/reset behavior.
 */
class ReviewApprovalDataServiceIntegrationTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posDataService: PosDataService

    @Autowired
    private lateinit var userDataService: UserDataService

    @Autowired
    private lateinit var reviewDataService: ReviewDataService

    @Autowired
    private lateinit var reviewApprovalDataService: ReviewApprovalDataService

    private fun seedReview(): Pair<UUID, UUID> {
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        val author = userDataService.upsert(TestFixtures.getUserFixturesForInsertion()[0])
        val approver = userDataService.upsert(TestFixtures.getUserFixturesForInsertion()[1])
        val review =
            reviewDataService.upsert(
                Review(
                    pos = pos,
                    author = author,
                    review = "A review long enough to pass validation.",
                    approvalCount = 0,
                    approved = false
                )
            )
        return requireNotNull(review.id) to requireNotNull(approver.id)
    }

    @Test
    fun `record persists an approval and the count reflects it`() {
        val (reviewId, approverId) = seedReview()

        assertThat(reviewApprovalDataService.countByReviewId(reviewId)).isEqualTo(0L)

        val recorded = reviewApprovalDataService.record(ReviewApproval(reviewId = reviewId, userId = approverId))

        assertThat(recorded.id).isNotNull()
        assertThat(recorded.createdAt).isNotNull()
        assertThat(reviewApprovalDataService.countByReviewId(reviewId)).isEqualTo(1L)
    }

    @Test
    fun `record throws DuplicationException when the same user approves the same review twice`() {
        val (reviewId, approverId) = seedReview()
        reviewApprovalDataService.record(ReviewApproval(reviewId = reviewId, userId = approverId))

        assertThatThrownBy {
            reviewApprovalDataService.record(ReviewApproval(reviewId = reviewId, userId = approverId))
        }.isInstanceOf(DuplicationException::class.java)
    }

    @Test
    fun `clear removes all approvals`() {
        val (reviewId, approverId) = seedReview()
        reviewApprovalDataService.record(ReviewApproval(reviewId = reviewId, userId = approverId))

        reviewApprovalDataService.clear()

        assertThat(reviewApprovalDataService.countByReviewId(reviewId)).isEqualTo(0L)
    }
}
