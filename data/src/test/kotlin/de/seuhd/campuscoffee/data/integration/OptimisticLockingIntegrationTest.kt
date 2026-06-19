package de.seuhd.campuscoffee.data.integration

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.orm.ObjectOptimisticLockingFailureException

/**
 * Optimistic locking on the review version column: two snapshots of the same review are loaded, the
 * first save succeeds, and the second save fails because its version is now stale.
 */
class OptimisticLockingIntegrationTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posEntityMapper: PosEntityMapper

    @Autowired
    private lateinit var userEntityMapper: UserEntityMapper

    @Test
    fun `saving a review with a stale version throws ObjectOptimisticLockingFailureException`() {
        val savedAuthor =
            userRepository.saveAndFlush(
                userEntityMapper.toEntity(TestFixtures.getUserFixturesForInsertion().first()).withGeneratedId()
            )
        val savedPos =
            posRepository.saveAndFlush(
                posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion().first()).withGeneratedId()
            )

        val reviewEntity =
            ReviewEntity()
                .apply {
                    pos = savedPos
                    author = savedAuthor
                    review = "Great place!"
                    approvalCount = 0
                    approved = false
                }.withGeneratedId()
        val id = reviewRepository.saveAndFlush(reviewEntity).id!!

        // each lookup returns a detached entity, so these are two independent snapshots at the initial version
        val first = reviewRepository.findByIdOrNull(id)!!
        val stale = reviewRepository.findByIdOrNull(id)!!

        // the first write succeeds and increments the version
        first.approvalCount = 1
        reviewRepository.saveAndFlush(first)

        // the second write carries the now-outdated version and must fail
        stale.approvalCount = 2
        assertThatThrownBy { reviewRepository.saveAndFlush(stale) }
            .isInstanceOf(ObjectOptimisticLockingFailureException::class.java)
    }
}
