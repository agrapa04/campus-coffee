package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [UserServiceImpl], which delegates to the [UserDataService] port: the login-name
 * lookup and the inherited id lookup must both resolve through that port.
 */
@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    @Mock
    private lateinit var userDataService: UserDataService

    private lateinit var userService: UserServiceImpl

    @BeforeEach
    fun setUp() {
        userService = UserServiceImpl(userDataService)
    }

    @Test
    fun getByLoginNameReturnsTheUserResolvedByTheDataService() {
        val user = TestFixtures.getUserFixtures().first()
        whenever(userDataService.getByLoginName(user.loginName)).thenReturn(user)

        assertThat(userService.getByLoginName(user.loginName)).isEqualTo(user)
        verify(userDataService).getByLoginName(user.loginName)
    }

    @Test
    fun getByIdResolvesThroughTheDataServicePort() {
        // also pins that the service exposes the injected port (a null port would fail this lookup)
        val user = TestFixtures.getUserFixtures().first()
        val id = requireNotNull(user.id)
        whenever(userDataService.getById(id)).thenReturn(user)

        assertThat(userService.getById(id)).isEqualTo(user)
        verify(userDataService).getById(id)
    }
}
