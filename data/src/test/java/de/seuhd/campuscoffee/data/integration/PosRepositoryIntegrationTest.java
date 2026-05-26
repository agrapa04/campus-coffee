package de.seuhd.campuscoffee.data.integration;

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper;
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link de.seuhd.campuscoffee.data.persistence.repositories.PosRepository}
 * against a real database.
 */
class PosRepositoryIntegrationTest extends AbstractDataIntegrationTest {

    @Autowired
    private PosEntityMapper posEntityMapper;

    @Test
    void findByNameReturnsMatchingPos() {
        PosEntity saved = posRepository.save(posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion().getFirst()));

        assertThat(posRepository.findByName(saved.getName()))
                .get().extracting(PosEntity::getId).isEqualTo(saved.getId());
        assertThat(posRepository.findByName("No Such POS")).isEmpty();
    }

    @Test
    void duplicateNameViolatesUniqueConstraint() {
        Pos pos = TestFixtures.getPosFixturesForInsertion().getFirst();
        posRepository.saveAndFlush(posEntityMapper.toEntity(pos));

        assertThatThrownBy(() -> posRepository.saveAndFlush(posEntityMapper.toEntity(pos)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
