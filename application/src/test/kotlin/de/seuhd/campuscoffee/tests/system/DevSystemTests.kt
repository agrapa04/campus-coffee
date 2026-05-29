package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.api.dtos.DevSummaryDto
import de.seuhd.campuscoffee.tests.SystemTestUtils.client
import de.seuhd.campuscoffee.tests.SystemTestUtils.posRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.returnResult

/**
 * System tests for the data management endpoints, available only in the `dev` profile.
 * The shared base class clears the data before each test, so the tests start from an empty database.
 */
@ActiveProfiles("dev")
class DevSystemTests : AbstractSystemTest() {
    @Test
    fun `PUT replaces all data with the fixtures and is idempotent`() {
        val first =
            client()
                .put()
                .uri("/api/dev/data")
                .exchange()
                .returnResult<DevSummaryDto>()
        assertThat(first.status.value()).isEqualTo(HttpStatus.OK.value())
        assertThat(posRequests.retrieveAll()).isNotEmpty()

        // a second PUT replaces rather than appends, so it yields the same counts (no duplicate-key error)
        val second =
            client()
                .put()
                .uri("/api/dev/data")
                .exchange()
                .returnResult<DevSummaryDto>()
        assertThat(second.status.value()).isEqualTo(HttpStatus.OK.value())
        assertThat(second.responseBody).isEqualTo(first.responseBody)
    }

    @Test
    fun `GET returns the current counts and DELETE clears all data`() {
        client()
            .put()
            .uri("/api/dev/data")
            .exchange()
            .returnResult<DevSummaryDto>()

        val counts =
            client()
                .get()
                .uri("/api/dev/data")
                .exchange()
                .returnResult<DevSummaryDto>()
        assertThat(counts.status.value()).isEqualTo(HttpStatus.OK.value())
        assertThat(counts.responseBody!!.pos).isEqualTo(posRequests.retrieveAll().size)

        val cleared =
            client()
                .delete()
                .uri("/api/dev/data")
                .exchange()
                .returnResult<ByteArray>()
        assertThat(cleared.status.value()).isEqualTo(HttpStatus.NO_CONTENT.value())
        assertThat(posRequests.retrieveAll()).isEmpty()

        val empty =
            client()
                .get()
                .uri("/api/dev/data")
                .exchange()
                .returnResult<DevSummaryDto>()
        assertThat(empty.responseBody!!.pos).isEqualTo(0)
    }
}
