package de.seuhd.campuscoffee.data.integration

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

/**
 * Integration tests for [de.seuhd.campuscoffee.data.persistence.repositories.PosRepository] against a
 * real database.
 */
class PosRepositoryIntegrationTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posEntityMapper: PosEntityMapper

    @Test
    fun `findByName returns the matching POS and null when none matches`() {
        val entity = posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion().first()).withGeneratedId()
        val saved = posRepository.save(entity)

        assertThat(posRepository.findByName(saved.name!!)?.id).isEqualTo(saved.id)
        assertThat(posRepository.findByName("No Such POS")).isNull()
    }

    @Test
    fun `saving two POS with the same name throws DataIntegrityViolationException`() {
        val pos = TestFixtures.getPosFixturesForInsertion().first()
        posRepository.saveAndFlush(posEntityMapper.toEntity(pos).withGeneratedId())

        // a distinct id, so it is the unique-name constraint that trips (not the primary key)
        assertThatThrownBy { posRepository.saveAndFlush(posEntityMapper.toEntity(pos).withGeneratedId()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
