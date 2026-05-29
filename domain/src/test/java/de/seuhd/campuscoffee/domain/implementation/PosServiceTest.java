package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit and integration tests for the operations related to POS (Point of Sale).
 */
@ExtendWith(MockitoExtension.class)
public class PosServiceTest {
    @Mock
    private PosDataService posDataService;

    @Mock
    private OsmDataService osmDataService;

    @InjectMocks
    private PosServiceImpl posService;

    @Test
    void getAllPosRetrievesExpectedPos() {
        // given
        List<Pos> testFixtures = TestFixtures.getPosFixtures();
        when(posDataService.getAll()).thenReturn(testFixtures);

        // when
        List<Pos> retrievedPos = posService.getAll();

        // then
        verify(posDataService).getAll();
        assertEquals(testFixtures.size(), retrievedPos.size());
        assertThat(retrievedPos)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(testFixtures);
    }

    @Test
    void getPosByIdNotFound() {
        // given
        when(posDataService.getById(anyLong())).thenThrow(new NotFoundException(Pos.class, 1L));

        // when, then
        assertThrows(NotFoundException.class, () -> posService.getById(anyLong()));
        verify(posDataService).getById(anyLong());

    }

    @Test
    void getPosByIdFound() {
        // given
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        Objects.requireNonNull(pos.id());
        when(posDataService.getById(pos.id())).thenReturn(pos);

        // when
        Pos retrievedPos = posService.getById(pos.id());

        // then
        verify(posDataService).getById(pos.id());
        assertThat(retrievedPos)
                .usingRecursiveComparison()
                .isEqualTo(pos);
    }

    @Test
    void upsertPosNotFound() {
        // given
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        Objects.requireNonNull(pos.id());
        when(posDataService.getById(pos.id())).thenThrow(new NotFoundException(Pos.class, pos.id()));

        // when, then
        assertThrows(NotFoundException.class, () -> posService.upsert(pos));
        verify(posDataService).getById(pos.id());
    }


    @Test
    void upsertNewPos() {
        // given
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        pos = pos.toBuilder().id(null).build();
        when(posDataService.upsert(pos)).thenReturn(pos.toBuilder().id(1L).build());

        // when, then
        posService.upsert(pos);

        verify(posDataService).upsert(pos);
    }

    @Test
    void getPosByName() {
        // given
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        when(posDataService.getByName(pos.name())).thenReturn(pos);

        // when
        Pos retrievedPos = posService.getByName(pos.name());

        // then
        assertThat(retrievedPos)
                .usingRecursiveComparison()
                .isEqualTo(pos);
        verify(posDataService).getByName(pos.name());
    }
}

