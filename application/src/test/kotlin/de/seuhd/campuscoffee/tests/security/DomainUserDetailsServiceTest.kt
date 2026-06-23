package de.seuhd.campuscoffee.tests.security

import de.seuhd.campuscoffee.api.security.DomainUserDetailsService
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.Role
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * Unit tests for [DomainUserDetailsService], which adapts a domain [User] to a Spring Security
 * UserDetails: roles become `ROLE_<role>` authorities, the stored hash becomes the password, and both a
 * missing hash and an unknown login name map to [UsernameNotFoundException].
 */
@ExtendWith(MockitoExtension::class)
class DomainUserDetailsServiceTest {
    @Mock
    private lateinit var userService: UserService

    private val service by lazy { DomainUserDetailsService(userService) }

    private fun user(
        roles: Set<Role>,
        passwordHash: String?
    ) = User(
        id = 1L,
        loginName = "jane_doe",
        emailAddress = "jane.doe@uni-heidelberg.de",
        firstName = "Jane",
        lastName = "Doe",
        roles = roles,
        passwordHash = passwordHash
    )

    @Test
    fun `loadUserByUsername maps roles to ROLE_ authorities and exposes the stored hash`() {
        whenever(userService.getByLoginName("jane_doe"))
            .thenReturn(user(setOf(Role.USER, Role.ADMIN), "{bcrypt}HASH"))

        val details = service.loadUserByUsername("jane_doe")

        assertThat(details.username).isEqualTo("jane_doe")
        assertThat(details.password).isEqualTo("{bcrypt}HASH")
        assertThat(details.authorities.map { it.authority })
            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN")
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException when the user has no stored hash`() {
        whenever(userService.getByLoginName("jane_doe")).thenReturn(user(setOf(Role.USER), null))

        assertThatThrownBy { service.loadUserByUsername("jane_doe") }
            .isInstanceOf(UsernameNotFoundException::class.java)
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException for an unknown login name`() {
        whenever(userService.getByLoginName("ghost"))
            .thenThrow(NotFoundException(User::class.java, "login name", "ghost"))

        assertThatThrownBy { service.loadUserByUsername("ghost") }
            .isInstanceOf(UsernameNotFoundException::class.java)
    }
}
