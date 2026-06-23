package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.domain.model.objects.persistedId
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Drives concurrent review approvals against the database, the scenario the review's optimistic locking
 * version exists for. Two different users approve the same review at the same time: both
 * write the versioned review row from the same loaded version, so one loses the optimistic locking race. The
 * bounded retry in [de.seuhd.campuscoffee.domain.implementation.ReviewServiceImpl.approve] re-runs the loser
 * in a fresh transaction, so both approvals are recorded and the derived count is consistent rather than one
 * approval being silently dropped. Extends [AbstractSystemTest] for the database and context;
 * [de.seuhd.campuscoffee.tests.system.EventSourcingReviewApprovalConcurrencyTest] re-runs it in event
 * sourcing mode.
 */
open class ReviewApprovalConcurrencyTest : AbstractSystemTest() {
    @Test
    fun `two users approving the same review concurrently both succeed and the count stays consistent`() {
        val users = userService.getAll()
        val author = users[0]
        val approverOne = users[1]
        val approverTwo = users[2]

        val pos = TestFixtures.createPosFixtures(posService).first()
        val review =
            reviewService.create(
                TestFixtures.getReviewFixturesForInsertion().first().copy(pos = pos),
                author
            )
        val reviewId = review.persistedId

        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures =
                listOf(approverOne, approverTwo).map { approver ->
                    executor.submit {
                        // line both threads up so they contend on the same review version
                        barrier.await()
                        reviewService.approve(reviewId, approver)
                    }
                }
            // neither approval is dropped: a get() rethrows any exception from the worker thread
            futures.forEach { it.get(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        // both distinct approvers were recorded, and the review's derived count matches the recorded rows
        assertThat(reviewApprovalDataService.countByReviewId(reviewId)).isEqualTo(2L)
        assertThat(reviewService.getById(reviewId).approvalCount).isEqualTo(2)
    }

    private companion object {
        private const val THREAD_TIMEOUT_SECONDS = 30L
    }
}
