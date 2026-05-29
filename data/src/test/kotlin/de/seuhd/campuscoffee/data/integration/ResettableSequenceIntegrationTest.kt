package de.seuhd.campuscoffee.data.integration

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Verifies that resetSequence restarts the id sequence, so ids are predictable after a reset.
 */
class ResettableSequenceIntegrationTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posEntityMapper: PosEntityMapper

    @Test
    fun `resetSequence restarts the id sequence at one`() {
        posRepository.resetSequence()

        val first = posRepository.saveAndFlush(posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion()[0]))
        val second = posRepository.saveAndFlush(posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion()[1]))

        assertThat(first.id).isEqualTo(1L)
        assertThat(second.id).isEqualTo(2L)
    }
}
