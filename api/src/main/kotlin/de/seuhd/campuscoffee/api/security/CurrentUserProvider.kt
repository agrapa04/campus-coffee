package de.seuhd.campuscoffee.api.security

import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.api.UserService
import org.springframework.stereotype.Component

/**
 * Resolves the authenticated principal to the domain [User] acting on the current request.
 *
 * This is the api layer's bridge between Spring Security and the domain (its counterpart, used while
 * verifying credentials, is [DomainUserDetailsService]): controllers pass the resolved [User] inward
 * so the domain decides ownership and roles without ever touching a Spring `Authentication`.
 */
@Component
class CurrentUserProvider(
    private val userService: UserService
) {
    /**
     * The domain [User] for the authenticated principal of the current request.
     *
     * TODO (Exercise 2): read the authenticated principal from Spring Security's request-scoped context
     *  (`SecurityContextHolder`). Its name is the login name, the same value whether the request
     *  authenticated via HTTP Basic or a JWT bearer token. Resolve that login name to a domain [User]
     *  through the injected [UserService]. Throw if there is no authenticated user (the security filter
     *  chain should already have rejected such a request with 401 before it reaches here).
     */
    fun currentUser(): User = TODO("Exercise 2: resolve the authenticated principal to a domain User")
}
