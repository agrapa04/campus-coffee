package de.seuhd.campuscoffee.data.integration;

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the data service turns a database uniqueness violation into a domain
 * {@link DuplicationException}, covering the constraint discovery and mapping in
 * {@link de.seuhd.campuscoffee.data.implementations.CrudDataServiceImpl}.
 */
class CrudDataServiceDuplicationTest extends AbstractDataIntegrationTest {

    @Autowired
    private PosDataService posDataService;

    @Autowired
    private UserDataService userDataService;

    @Test
    void duplicatePosNameThrowsDuplicationException() {
        Pos pos = TestFixtures.getPosFixturesForInsertion().getFirst();
        posDataService.upsert(pos);

        assertThatThrownBy(() -> posDataService.upsert(pos)).isInstanceOf(DuplicationException.class);
    }

    @Test
    void duplicateUserEmailThrowsDuplicationException() {
        User user = TestFixtures.getUserFixturesForInsertion().getFirst();
        userDataService.upsert(user);
        // same email but a different login name, so the email uniqueness constraint is the one violated
        User sameEmail = user.toBuilder().loginName(user.loginName() + "_other").build();

        assertThatThrownBy(() -> userDataService.upsert(sameEmail)).isInstanceOf(DuplicationException.class);
    }

    @Test
    void nonUniqueViolationIsRethrownNotMappedToDuplication() {
        // an empty description violates a CHECK constraint, not a uniqueness constraint, so the data
        // service must propagate the original exception rather than wrap it as a DuplicationException
        Pos invalid = TestFixtures.getPosFixturesForInsertion().getFirst().toBuilder().description("").build();

        assertThatThrownBy(() -> posDataService.upsert(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicationException.class);
    }
}
