package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.mapper.ReviewApprovalEntityMapper
import de.seuhd.campuscoffee.data.mapper.ReviewEntityMapper
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.data.persistence.entities.UserEntity
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewApprovalRepository
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository
import de.seuhd.campuscoffee.data.persistence.repositories.UserRepository
import de.seuhd.campuscoffee.domain.exceptions.ConcurrentUpdateException
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.util.Optional
import java.util.UUID

/**
 * Unit test for the [ReadModelProjector]'s exception translation, with mocked repositories so the
 * relational failures can be forced deterministically. Mirrors the relational adapter's optimistic locking
 * mapping test: a stale UPDATE event surfaces as a [ConcurrentUpdateException], the same exception the
 * relational adapter raises.
 */
class ReadModelProjectorExceptionMappingTest {
    private val posRepository = mock<PosRepository>()
    private val userRepository = mock<UserRepository>()
    private val reviewRepository = mock<ReviewRepository>()
    private val reviewApprovalRepository = mock<ReviewApprovalRepository>()
    private val posMapper = mock<PosEntityMapper>()
    private val userMapper = mock<UserEntityMapper>()
    private val reviewMapper = mock<ReviewEntityMapper>()
    private val reviewApprovalMapper = mock<ReviewApprovalEntityMapper>()

    private val projector =
        ReadModelProjector(
            posRepository,
            userRepository,
            reviewRepository,
            reviewApprovalRepository,
            posMapper,
            userMapper,
            reviewMapper,
            reviewApprovalMapper
        )

    @Test
    fun `a stale Review UPDATE event maps an optimistic locking failure to ConcurrentUpdateException`() {
        val pos = TestFixtures.anyPos()
        val author = TestFixtures.getUserFixtures().first()
        val reviewId = UUID.randomUUID()
        // reconstructReview resolves the POS and author from the read model
        whenever(posRepository.findById(any())).thenReturn(Optional.of(PosEntity()))
        whenever(userRepository.findById(any())).thenReturn(Optional.of(UserEntity()))
        whenever(posMapper.fromEntity(any())).thenReturn(pos)
        whenever(userMapper.fromEntity(any())).thenReturn(author)
        // the target review row exists (UPDATE path uses updateEntity, not toEntity), but the write fails
        // the optimistic locking check
        whenever(reviewRepository.findById(reviewId)).thenReturn(Optional.of(ReviewEntity()))
        whenever(reviewRepository.saveAndFlush(any<ReviewEntity>()))
            .thenThrow(ObjectOptimisticLockingFailureException(ReviewEntity::class.java, reviewId))

        val body =
            EventBodies.review(
                id = reviewId,
                posId = requireNotNull(pos.id),
                authorId = requireNotNull(author.id)
            )

        assertThatThrownBy { projector.apply(ChangeType.UPDATE, "Review", body) }
            .isInstanceOf(ConcurrentUpdateException::class.java)
    }
}
