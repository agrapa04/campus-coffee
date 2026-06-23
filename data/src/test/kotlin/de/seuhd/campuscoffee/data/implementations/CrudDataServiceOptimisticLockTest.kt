package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository
import de.seuhd.campuscoffee.domain.exceptions.ConcurrentUpdateException
import de.seuhd.campuscoffee.domain.ports.IdGenerator
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
 * The repository is mocked to throw a JPA optimistic locking failure on save; upsert should map it to a
 * [ConcurrentUpdateException].
 */
class CrudDataServiceOptimisticLockTest {
    @Test
    fun `upsert maps a JPA optimistic lock failure to ConcurrentUpdateException`() {
        val repository = mock<PosRepository>()
        val mapper = mock<PosEntityMapper>()
        // the update path does not assign a new id, but the constructor requires a generator
        val service = PosDataServiceImpl(repository, mapper, IdGenerator { UUID.randomUUID() })

        val existing = TestFixtures.anyPos() // has a non-null id, so upsert takes the update path
        val id = existing.id!!
        whenever(repository.findById(id)).thenReturn(Optional.of(PosEntity()))
        whenever(repository.saveAndFlush(any<PosEntity>()))
            .thenThrow(ObjectOptimisticLockingFailureException(PosEntity::class.java, id))

        assertThatThrownBy { service.upsert(existing) }
            .isInstanceOf(ConcurrentUpdateException::class.java)
    }
}
