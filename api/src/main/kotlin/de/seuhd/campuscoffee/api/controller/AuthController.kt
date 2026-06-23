package de.seuhd.campuscoffee.api.controller

import de.seuhd.campuscoffee.api.dtos.TokenRequestDto
import de.seuhd.campuscoffee.api.dtos.TokenResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Authentication endpoint that exchanges credentials for a stateless JWT bearer token. The path is
 * relative to the resource; the central `/api` base is applied by ApiWebConfig.
 *
 * Skeleton in the starter: the endpoint is reachable but unimplemented and answers 501 Not Implemented.
 * Exercise 4 wires it to the AuthenticationManager and returns a JWT (subject = login name, a `roles`
 * claim, a 15-minute expiry) built with the provided JwtEncoder.
 */
@Tag(name = "Authentication", description = "Exchange credentials for a stateless JWT bearer token.")
@Controller
@RequestMapping("/auth")
class AuthController {
    @Operation(summary = "Authenticate and return a JWT bearer token (not yet implemented).")
    @PostMapping("/token")
    fun token(
        @RequestBody
        @Valid request: TokenRequestDto
    ): ResponseEntity<TokenResponseDto> {
        log.info("Token requested for login name '{}' (endpoint not yet implemented).", request.loginName)
        // TODO (Exercise 4): authenticate the credentials via the AuthenticationManager and build a JWT
        //  (subject = login name, a `roles` claim, a 15-minute expiry) with the provided JwtEncoder,
        //  returning 200 OK with the token. Until then the endpoint advertises itself as unimplemented.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AuthController::class.java)
    }
}
