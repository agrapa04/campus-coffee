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
 * Bridges the domain `User` store and Spring Security while credentials are verified: implements Spring's
 * [UserDetailsService] so the `DaoAuthenticationProvider` can load the stored credentials. Loads a user by
 * login name via [UserService] and adapts it to a Spring Security [UserDetails], with the stored password
 * hash and `ROLE_<role>` authorities derived from the user's roles. The counterpart that runs once a request
 * is authenticated is [CurrentUserProvider]; the authorization rules themselves live in the application's
 * security configuration.
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
