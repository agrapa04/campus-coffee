package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.api.dtos.TokenRequestDto
import de.seuhd.campuscoffee.api.dtos.TokenResponseDto
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.Credentials
import de.seuhd.campuscoffee.tests.SystemTestUtils.MODERATOR
import de.seuhd.campuscoffee.tests.SystemTestUtils.USER
import de.seuhd.campuscoffee.tests.SystemTestUtils.client
import de.seuhd.campuscoffee.tests.SystemTestUtils.posRequests
import de.seuhd.campuscoffee.tests.SystemTestUtils.reviewRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.client.returnResult
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * System tests for the JWT bearer-token flow (Exercise 4): the token endpoint exchanges credentials for
 * a signed token, and a request carrying that token resolves to the same principal as HTTP Basic, so the
 * role rules from Exercises 1 to 3 hold under Bearer. An expired or tampered token is rejected with 401.
 */
class JwtAuthSystemTests : AbstractSystemTest() {
    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @Test
    fun `requesting a token with valid credentials returns a token and with wrong credentials returns 401`() {
        // valid credentials yield a non-blank token
        assertThat(tokenFor(MODERATOR)).isNotBlank()

        // a wrong password never yields a token; the entry point renders a JSON 401
        assertThat(tokenRequestStatus(MODERATOR.loginName, "definitely-the-wrong-password"))
            .isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `a write request with a fresh bearer token succeeds and a tampered token returns 401`() {
        val token = tokenFor(MODERATOR)
        val dto = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first())

        // a moderator's token authorizes a POS create, exactly as the moderator's Basic credentials would
        assertThat(posRequests.createWithBearerAndReturnStatusCode(dto, token))
            .isEqualTo(HttpStatus.CREATED.value())

        // break the signature so the token no longer authenticates. Flip a character at the start of the
        // signature, not the last one: a 32-byte HMAC ends on a base64url character with two unused low
        // bits, so changing only that last character can still decode to the same signature.
        val signatureStart = token.lastIndexOf('.') + 1
        val tampered =
            token.substring(0, signatureStart) +
                (if (token[signatureStart] == 'A') 'B' else 'A') +
                token.substring(signatureStart + 1)
        assertThat(posRequests.createWithBearerAndReturnStatusCode(dto, tampered))
            .isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `an expired bearer token returns 401`() {
        val expired =
            forgeToken(MODERATOR.loginName, listOf("USER", "MODERATOR"), Instant.now().minus(10, ChronoUnit.MINUTES))
        val dto = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first())

        // the signature is valid, but the resource server rejects the token on its expiry
        assertThat(posRequests.createWithBearerAndReturnStatusCode(dto, expired))
            .isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `a USER's token cannot curate POS and is forbidden with 403`() {
        val token = tokenFor(USER)
        val dto = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first())

        // the token carries the USER role only, so POS curation is forbidden under Bearer just as it is
        // under Basic
        assertThat(posRequests.createWithBearerAndReturnStatusCode(dto, token))
            .isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun `a bearer token approves a review as its subject`() {
        val pos = createPos()
        val (_, authorCredentials) = createUser("jwt_author", "jwt.author@uni-heidelberg.de")
        val (_, approverCredentials) = createUser("jwt_approver", "jwt.approver@uni-heidelberg.de")
        val review =
            reviewRequests
                .create(
                    listOf(ReviewDto(posId = pos.id, review = "A review approved over a bearer token.")),
                    authorCredentials
                ).first()

        // the approver authenticates with a bearer token; the approval is recorded under the token's subject
        val token = tokenFor(approverCredentials)
        assertThat(reviewRequests.approveWithBearerAndReturnStatusCode(review.id!!, token))
            .isEqualTo(HttpStatus.OK.value())

        // the token's subject really drives the rule: the author's own token is rejected as a
        // self-approval (400), which would not happen if the subject resolved to anyone else
        val authorToken = tokenFor(authorCredentials)
        assertThat(reviewRequests.approveWithBearerAndReturnStatusCode(review.id!!, authorToken))
            .isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    // helpers ---------------------------------------------------------------------

    private fun requestToken(
        loginName: String,
        password: String
    ) = client()
        .post()
        .uri("/api/auth/token")
        .contentType(MediaType.APPLICATION_JSON)
        .body(TokenRequestDto(loginName = loginName, password = password))
        .exchange()

    /** The status code of a token request, read without parsing the body (which is not a token on a 401). */
    private fun tokenRequestStatus(
        loginName: String,
        password: String
    ): Int = requestToken(loginName, password).returnResult<ByteArray>().status.value()

    private fun tokenFor(credentials: Credentials): String {
        val result = requestToken(credentials.loginName, credentials.password).returnResult<TokenResponseDto>()
        assertThat(result.status.value()).isEqualTo(HttpStatus.OK.value())
        return result.responseBody!!.token
    }

    /** Signs a token with the application's key but arbitrary claims, to exercise the expiry path. */
    private fun forgeToken(
        loginName: String,
        roles: List<String>,
        expiresAt: Instant
    ): String {
        val claims =
            JwtClaimsSet
                .builder()
                .subject(loginName)
                .claim("roles", roles)
                .issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES))
                .expiresAt(expiresAt)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    private fun createPos(): Pos = posService.upsert(TestFixtures.getPosFixturesForInsertion().first())

    /** Creates a user with a known password and returns it together with its credentials. */
    private fun createUser(
        loginName: String,
        emailAddress: String
    ): Pair<User, Credentials> {
        val password = "test-password-$loginName"
        val user =
            userService.upsert(
                User(
                    loginName = loginName,
                    emailAddress = emailAddress,
                    firstName = "First",
                    lastName = "Last",
                    password = password
                )
            )
        return user to Credentials(loginName, password)
    }
}
