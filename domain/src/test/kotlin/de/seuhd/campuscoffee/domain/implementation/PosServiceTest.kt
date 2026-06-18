package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit and integration tests for the operations related to POS (Point of Sale).
 */
@ExtendWith(MockitoExtension::class)
class PosServiceTest {
    @Mock
    private lateinit var posDataService: PosDataService

    private lateinit var posService: PosServiceImpl

    @BeforeEach
    fun setUp() {
        // osmDataService is only a construction dependency; OSM import is covered by PosTypeMappingTest
        posService = PosServiceImpl(posDataService, mock())
    }

    @Test
    fun `getAll returns the POS from the data service`() {
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
    fun `getById propagates NotFoundException from the data service`() {
        // a literal id, not a matcher: the service under test is a real object, and passing a matcher
        // as a method argument corrupts Mockito's matcher stack
        whenever(posDataService.getById(1L)).thenThrow(NotFoundException(Pos::class.java, 1L))

        assertThrows<NotFoundException> { posService.getById(1L) }
        verify(posDataService).getById(1L)
    }

    @Test
    fun `getById returns the POS from the data service`() {
        val pos = TestFixtures.anyPos()
        val id = requireNotNull(pos.id)
        whenever(posDataService.getById(id)).thenReturn(pos)

        val retrievedPos = posService.getById(id)

        verify(posDataService).getById(id)
        assertThat(retrievedPos).usingRecursiveComparison().isEqualTo(pos)
    }

    @Test
    fun `upsert propagates NotFoundException when updating a missing POS`() {
        val pos = TestFixtures.anyPos()
        val id = requireNotNull(pos.id)
        whenever(posDataService.getById(id)).thenThrow(NotFoundException(Pos::class.java, id))

        assertThrows<NotFoundException> { posService.upsert(pos) }
        verify(posDataService).getById(id)
    }

    @Test
    fun `upsert of a new POS delegates to the data service`() {
        val pos = TestFixtures.anyPos().copy(id = null)
        whenever(posDataService.upsert(pos)).thenReturn(pos.copy(id = 1L))

        posService.upsert(pos)

        verify(posDataService).upsert(pos)
    }

    @Test
    fun `getByName returns the POS from the data service`() {
        val pos = TestFixtures.anyPos()
        whenever(posDataService.getByName(pos.name)).thenReturn(pos)

        val retrievedPos = posService.getByName(pos.name)

        assertThat(retrievedPos).usingRecursiveComparison().isEqualTo(pos)
        verify(posDataService).getByName(pos.name)
    }
}
