package de.seuhd.campuscoffee.data.integration;

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper;
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persists a POS with a house number suffix and reads it back from the database, confirming that the
 * embedded address columns round-trip through the {@link PosEntityMapper} split and merge.
 */
class PosEntityMapperRoundTripTest extends AbstractDataIntegrationTest {

    @Autowired
    private PosEntityMapper posEntityMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    void houseNumberSuffixSurvivesPersistenceRoundTrip() {
        Pos pos = TestFixtures.getPosFixturesForInsertion().getFirst().toBuilder().houseNumber("99a").build();
        Long id = posRepository.saveAndFlush(posEntityMapper.toEntity(pos)).getId();

        // detach everything so the read comes from the database, not the persistence context
        entityManager.clear();

        PosEntity reloaded = posRepository.findById(id).orElseThrow();
        assertThat(reloaded.getAddress().getHouseNumber()).isEqualTo(99);
        assertThat(reloaded.getAddress().getHouseNumberSuffix()).isEqualTo('a');
        assertThat(posEntityMapper.fromEntity(reloaded).houseNumber()).isEqualTo("99a");
    }
}
