package de.seuhd.campuscoffee.data.integration;

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper;
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code resetSequence} restarts the id sequence, so ids are predictable after a reset.
 */
class ResettableSequenceIntegrationTest extends AbstractDataIntegrationTest {

    @Autowired
    private PosEntityMapper posEntityMapper;

    @Test
    void resetSequenceRestartsIdsAtOne() {
        posRepository.resetSequence();

        PosEntity first = posRepository.saveAndFlush(posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion().get(0)));
        PosEntity second = posRepository.saveAndFlush(posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion().get(1)));

        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(2L);
    }
}
