package de.seuhd.campuscoffee.api.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * Spring Security configuration.
 *
 * The filter chain is stateless (no server-side session) with CSRF disabled — CSRF protects the
 * cookie-based sessions this credential- and token-based API never uses. Authentication arrives via HTTP
 * Basic or a JWT bearer token; both resolve to the same principal (login name + ROLE_* authorities), so
 * the authorization rules below are identical regardless of the mechanism.
 *
 * The access rules implement the assignment's matrix: reads (`GET`) and user registration are public,
 * every other write request requires authentication, POS curation requires `MODERATOR`, and managing or deleting
 * another user requires `ADMIN`. The finer ownership rules (a review's author; a user editing only their
 * own account; the role-change rule) depend on which resource is targeted, so they live in the domain
 * services, not here.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticationEntryPoint: AuthenticationEntryPoint,
        accessDeniedHandler: AccessDeniedHandler,
        jwtAuthenticationConverter: JwtAuthenticationConverter
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                // Swagger UI, the OpenAPI docs, and the dev-only data endpoints stay reachable (the dev
                // endpoints are registered only under the dev profile, but the matcher is harmless otherwise).
                authorize("/api/swagger-ui.html", permitAll)
                authorize("/api/swagger-ui/**", permitAll)
                authorize("/api/api-docs/**", permitAll)
                authorize("/api/dev/**", permitAll)
                // Authenticating happens before the user exists / before a token is held: registration and
                // the token endpoint must stay open.
                authorize(HttpMethod.POST, "/api/users", permitAll)
                authorize("/api/auth/token", permitAll)
                // Reading user data is not public: it would expose login names, email addresses, and
                // role assignments. Listing all users is admin-only; a single user (by id or login name)
                // is visible only to that user or an admin — the self-or-admin rule depends on which user
                // is targeted, so it is enforced in the domain, and the URL rule only requires a login.
                authorize(HttpMethod.GET, "/api/users", hasRole("ADMIN"))
                authorize(HttpMethod.GET, "/api/users/**", authenticated)
                // Actuator: health is public (it reports only an UP/DOWN status, so it is safe to
                // expose); metrics is admin-only. These must precede the public GET catch-all below,
                // which would otherwise make them anonymous. env carries no rule here; application.yaml
                // controls where it is exposed.
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/actuator/metrics", hasRole("ADMIN"))
                authorize("/actuator/metrics/**", hasRole("ADMIN"))
                // All other reads (the POS directory and reviews) are public.
                authorize(HttpMethod.GET, "/**", permitAll)
                // POS curation (create/update/delete and the OSM import) requires a moderator.
                authorize(HttpMethod.POST, "/api/pos/**", hasRole("MODERATOR"))
                authorize(HttpMethod.PUT, "/api/pos/**", hasRole("MODERATOR"))
                authorize(HttpMethod.DELETE, "/api/pos/**", hasRole("MODERATOR"))
                // Deleting a user is admin-only; editing another user is enforced in the domain (it depends
                // on whether the target is the caller's own account), so PUT only requires authentication here.
                authorize(HttpMethod.DELETE, "/api/users/**", hasRole("ADMIN"))
                // Every remaining write request requires an authenticated user; the domain decides the finer rules.
                authorize(anyRequest, authenticated)
            }
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            httpBasic { }
            // Bearer-token (JWT) resource server: a valid token authenticates the request, its `roles`
            // claim mapped to ROLE_* authorities so the rules above apply unchanged under Bearer.
            oauth2ResourceServer {
                jwt { this.jwtAuthenticationConverter = jwtAuthenticationConverter }
            }
            // Render auth failures as the application's JSON ErrorResponse: a missing or invalid
            // credential as 401, and an authenticated caller hitting a role-gated URL as 403.
            exceptionHandling {
                this.authenticationEntryPoint = authenticationEntryPoint
                this.accessDeniedHandler = accessDeniedHandler
            }
        }
        return http.build()
    }

    /**
     * Maps a validated JWT to an authentication: the custom `roles` claim (bare role names) becomes
     * `ROLE_*` authorities and the principal name is the token subject (the login name). This makes a
     * Bearer principal indistinguishable from a Basic one for the authorization rules.
     */
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
            val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
            roles.map { SimpleGrantedAuthority("ROLE_$it") }
        }
        converter.setPrincipalClaimName("sub")
        return converter
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
