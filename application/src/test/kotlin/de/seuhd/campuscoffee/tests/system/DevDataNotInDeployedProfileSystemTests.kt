package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.tests.SystemTestUtils.client
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.client.returnResult

/**
 * Verifies the dev data endpoints are absent outside the `dev` profile. The base test context runs without
 * the `dev` profile, so the `@Profile("dev")` `DevController` is not registered and `/api/dev/data` has no
 * handler, confirming the endpoints are reachable only in local development.
 */
class DevDataNotInDeployedProfileSystemTests : AbstractSystemTest() {
    @Test
    fun `requesting the dev data endpoint outside the dev profile returns 404 Not Found`() {
        val result =
            client()
                .get()
                .uri("/api/dev/data")
                .exchange()
                .returnResult<ByteArray>()

        assertThat(result.status.value()).isEqualTo(HttpStatus.NOT_FOUND.value())
    }
}
