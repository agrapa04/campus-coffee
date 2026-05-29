package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit and integration tests for the operations related to POS (Point of Sale).
 */
@ExtendWith(MockitoExtension::class)
class PosServiceTest {
    @Mock
    private lateinit var posDataService: PosDataService

    @Mock
    private lateinit var osmDataService: OsmDataService

    @InjectMocks
    private lateinit var posService: PosServiceImpl

    @Test
    fun getAllPosRetrievesExpectedPos() {
        val testFixtures = TestFixtures.getPosFixtures()
        whenever(posDataService.getAll()).thenReturn(testFixtures)

        val retrievedPos = posService.getAll()

        verify(posDataService).getAll()
        assertEquals(testFixtures.size, retrievedPos.size)
        assertThat(retrievedPos)
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactlyInAnyOrderElementsOf(testFixtures)
    }

    @Test
    fun getPosByIdNotFound() {
        whenever(posDataService.getById(anyLong())).thenThrow(NotFoundException(Pos::class.java, 1L))

        assertThrows<NotFoundException> { posService.getById(anyLong()) }
        verify(posDataService).getById(anyLong())
    }

    @Test
    fun getPosByIdFound() {
        val pos = TestFixtures.getPosFixtures().first()
        val id = requireNotNull(pos.id)
        whenever(posDataService.getById(id)).thenReturn(pos)

        val retrievedPos = posService.getById(id)

        verify(posDataService).getById(id)
        assertThat(retrievedPos).usingRecursiveComparison().isEqualTo(pos)
    }

    @Test
    fun upsertPosNotFound() {
        val pos = TestFixtures.getPosFixtures().first()
        val id = requireNotNull(pos.id)
        whenever(posDataService.getById(id)).thenThrow(NotFoundException(Pos::class.java, id))

        assertThrows<NotFoundException> { posService.upsert(pos) }
        verify(posDataService).getById(id)
    }

    @Test
    fun upsertNewPos() {
        val pos = TestFixtures.getPosFixtures().first().copy(id = null)
        whenever(posDataService.upsert(pos)).thenReturn(pos.copy(id = 1L))

        posService.upsert(pos)

        verify(posDataService).upsert(pos)
    }

    @Test
    fun getPosByName() {
        val pos = TestFixtures.getPosFixtures().first()
        whenever(posDataService.getByName(pos.name)).thenReturn(pos)

        val retrievedPos = posService.getByName(pos.name)

        assertThat(retrievedPos).usingRecursiveComparison().isEqualTo(pos)
        verify(posDataService).getByName(pos.name)
    }
}
