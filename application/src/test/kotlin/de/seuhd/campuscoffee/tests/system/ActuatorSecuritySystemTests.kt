package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.tests.SystemTestUtils.ADMIN
import de.seuhd.campuscoffee.tests.SystemTestUtils.USER
import de.seuhd.campuscoffee.tests.SystemTestUtils.basicAuthHeader
import de.seuhd.campuscoffee.tests.SystemTestUtils.client
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.returnResult

/**
 * System tests for the Spring Boot Actuator access rules in `SecurityConfig`: exposing an endpoint in
 * `application.yaml` only makes it reachable, so each one needs an explicit authorization rule or it falls
 * through to the public read catch-all. Health is public; metrics and env are admin-only (env dumps the
 * configuration property sources). The runtime exposes only `health` outside the dev profile, so the suite
 * exposes `health, metrics, env` itself (merged with the base's `persistence.mode`) to exercise the rules.
 */
@TestPropertySource(properties = ["management.endpoints.web.exposure.include=health,metrics,env"])
class ActuatorSecuritySystemTests : AbstractSystemTest() {
    private fun getStatus(
        path: String,
        authHeader: String? = null
    ): Int =
        client()
            .get()
            .uri(path)
            .apply { authHeader?.let { header(HttpHeaders.AUTHORIZATION, it) } }
            .exchange()
            .returnResult<ByteArray>()
            .status
            .value()

    @Test
    fun `health is reachable without authentication (200 OK)`() {
        assertThat(getStatus("/actuator/health")).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    fun `metrics requires an admin (401 anonymous, 403 for a non-admin, 200 for an admin)`() {
        assertThat(getStatus("/actuator/metrics")).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        assertThat(getStatus("/actuator/metrics", basicAuthHeader(USER))).isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(getStatus("/actuator/metrics", basicAuthHeader(ADMIN))).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    fun `env requires an admin (401 anonymous, 403 for a non-admin, 200 for an admin)`() {
        assertThat(getStatus("/actuator/env")).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        assertThat(getStatus("/actuator/env", basicAuthHeader(USER))).isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(getStatus("/actuator/env", basicAuthHeader(ADMIN))).isEqualTo(HttpStatus.OK.value())
    }
}
