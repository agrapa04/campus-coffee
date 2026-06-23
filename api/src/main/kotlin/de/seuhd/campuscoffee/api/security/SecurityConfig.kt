package de.seuhd.campuscoffee.api.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security configuration.
 *
 * The starter ships a deliberately *permissive* chain so every endpoint stays open and the existing
 * open-endpoint tests keep passing: students tighten this into a real chain rather than starting from a
 * blank slate. The supporting beans (password encoder, authentication provider/manager, JSON 401 entry
 * point) and the JWT resource-server wiring are already in place so the authentication and JWT exercises
 * are about *policy*, not plumbing.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticationEntryPoint: AuthenticationEntryPoint
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                // TODO (Exercise 1): require authentication on write operations (POST/PUT/DELETE), keeping
                //  POS/review reads, registration (POST /users), and the Swagger / dev endpoints open. User
                //  data is not public: listing users (GET /users) is admin-only and a single user (GET
                //  /users/{id}, login-name filter) requires authentication; the finer self-or-admin rule for
                //  one user depends on the target, so it is enforced in the domain in Exercise 2. The starter
                //  leaves everything open so the app builds green with no auth enforced yet.
                // TODO (Exercise 3): curating a POS (POST/PUT/DELETE `/pos`) requires the `MODERATOR` role,
                //  and deleting a user (DELETE `/users/{id}`) requires `ADMIN`; add these rules before the
                //  catch-all so they take precedence.
                authorize(anyRequest, permitAll)
            }
            // Stateless API: no server-side session; the principal comes from the credentials on each request.
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            // Accept HTTP Basic credentials. Bearer-token (JWT) support is wired below.
            httpBasic { }
            // Bearer-token (JWT) resource server. Harmless under permitAll: a missing or invalid token
            // leaves the request anonymous, which permitAll still allows.
            // TODO (Exercise 4): add a JwtAuthenticationConverter that maps the token's `roles` claim to
            //  `ROLE_*` authorities, so a Bearer principal carries the same authorities as a Basic one.
            oauth2ResourceServer { jwt { } }
            // Render an unauthenticated rejection as the application's JSON ErrorResponse (takes effect
            // once the chain requires authentication).
            exceptionHandling { this.authenticationEntryPoint = authenticationEntryPoint }
        }
        return http.build()
    }

    /** Delegating encoder ({bcrypt} by default); shared with the data layer's hashing semantics. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /** Authenticates username/password against the [UserDetailsService] using the shared encoder. */
    @Bean
    fun authenticationProvider(
        userDetailsService: UserDetailsService,
        passwordEncoder: PasswordEncoder
    ): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return provider
    }

    /** Exposes the [AuthenticationManager] so the token endpoint (Exercise 4) can reuse it. */
    @Bean
    fun authenticationManager(authenticationProvider: DaoAuthenticationProvider): AuthenticationManager =
        AuthenticationManager { authentication -> authenticationProvider.authenticate(authentication) }
}
