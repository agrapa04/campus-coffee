package de.seuhd.campuscoffee.data.integration

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

/**
 * Persists a POS with a house number suffix and reads it back from the database, confirming that the
 * embedded address columns round-trip through the [PosEntityMapper] split and merge.
 */
class PosEntityMapperRoundTripTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posEntityMapper: PosEntityMapper

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `persisting and reloading a POS preserves the house number suffix`() {
        val pos = TestFixtures.getPosFixturesForInsertion().first().copy(houseNumber = "99a")
        val id = posRepository.saveAndFlush(posEntityMapper.toEntity(pos)).id!!

        // detach everything so the read comes from the database, not the persistence context
        entityManager.clear()

        val reloaded = posRepository.findByIdOrNull(id)!!
        assertThat(reloaded.address!!.houseNumber).isEqualTo(99)
        assertThat(reloaded.address!!.houseNumberSuffix).isEqualTo('a')
        assertThat(posEntityMapper.fromEntity(reloaded).houseNumber).isEqualTo("99a")
    }
}
