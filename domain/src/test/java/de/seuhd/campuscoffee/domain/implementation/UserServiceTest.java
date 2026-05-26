package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserServiceImpl}, which delegates to the {@link UserDataService} port: the
 * login-name lookup and the inherited id lookup must both resolve through that port.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserDataService userDataService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userDataService);
    }

    @Test
    void getByLoginNameReturnsTheUserResolvedByTheDataService() {
        User user = TestFixtures.getUserFixtures().getFirst();
        when(userDataService.getByLoginName(user.loginName())).thenReturn(user);

        assertThat(userService.getByLoginName(user.loginName())).isEqualTo(user);
        verify(userDataService).getByLoginName(user.loginName());
    }

    @Test
    void getByIdResolvesThroughTheDataServicePort() {
        // also pins that the service exposes the injected port (a null port would fail this lookup)
        User user = TestFixtures.getUserFixtures().getFirst();
        Long id = Objects.requireNonNull(user.getId());
        when(userDataService.getById(id)).thenReturn(user);

        assertThat(userService.getById(id)).isEqualTo(user);
        verify(userDataService).getById(id);
    }
}
