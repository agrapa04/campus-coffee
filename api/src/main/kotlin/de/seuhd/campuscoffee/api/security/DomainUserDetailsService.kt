package de.seuhd.campuscoffee.api.security

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.ports.api.UserService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.security.core.userdetails.User as SpringUser

/**
 * Loads a user by login name and adapts it to a Spring Security [UserDetails], with the stored password
 * hash and `ROLE_<role>` authorities derived from the user's roles. This is the bridge between the
 * domain `User` and Spring Security; the authorization rules themselves are defined in [SecurityConfig].
 *
 * Spring boilerplate provided in the starter.
 */
@Service
class DomainUserDetailsService(
    private val userService: UserService
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user =
            try {
                userService.getByLoginName(username)
            } catch (e: NotFoundException) {
                throw UsernameNotFoundException("No user with login name '$username'.", e)
            }

        val authorities = user.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }

        // A user without a stored hash has no usable credentials, so they cannot authenticate. Surface
        // that as an unknown user rather than building an account that no password could ever match.
        val storedHash =
            user.passwordHash
                ?: throw UsernameNotFoundException("User '$username' has no password set.")

        return SpringUser
            .withUsername(user.loginName)
            .password(storedHash)
            .authorities(authorities)
            .build()
    }
}
