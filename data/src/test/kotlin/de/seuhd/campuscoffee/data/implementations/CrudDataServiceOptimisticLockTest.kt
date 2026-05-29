package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository
import de.seuhd.campuscoffee.domain.exceptions.ConcurrentUpdateException
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.util.Optional

/**
 * The repository is mocked to throw a JPA optimistic-locking failure on save; upsert should map it to a
 * [ConcurrentUpdateException].
 */
class CrudDataServiceOptimisticLockTest {
    @Test
    fun optimisticLockFailureBecomesConcurrentUpdateException() {
        val repository = mock<PosRepository>()
        val mapper = mock<PosEntityMapper>()
        val service = PosDataServiceImpl(repository, mapper)

        val existing = TestFixtures.getPosFixtures().first() // has a non-null id, so upsert takes the update path
        val id = existing.id!!
        whenever(repository.findById(id)).thenReturn(Optional.of(PosEntity()))
        whenever(repository.saveAndFlush(any<PosEntity>()))
            .thenThrow(ObjectOptimisticLockingFailureException(PosEntity::class.java, id))

        assertThatThrownBy { service.upsert(existing) }
            .isInstanceOf(ConcurrentUpdateException::class.java)
    }
}
