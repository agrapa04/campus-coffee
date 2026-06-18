package de.seuhd.campuscoffee.tests.system

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.domain.model.enums.PosType
import de.seuhd.campuscoffee.tests.SystemTestUtils.Credentials
import de.seuhd.campuscoffee.tests.SystemTestUtils.MODERATOR
import de.seuhd.campuscoffee.tests.SystemTestUtils.USER
import de.seuhd.campuscoffee.tests.SystemTestUtils.basicAuthHeader
import de.seuhd.campuscoffee.tests.SystemTestUtils.client
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult

/**
 * System tests for importing a POS from an OpenStreetMap node. The external OSM HTTP API is stubbed
 * with WireMock, and the OSM client is pointed at the stub through the `osm.api.base-url` property.
 * The server starts before the Spring context so the client resolves the stub URL. The import is a POS
 * write request, so it requires a moderator; each call authenticates as the moderator fixture, and one test
 * pins that a plain USER is forbidden.
 */
class OsmImportSystemTests : AbstractSystemTest() {
    @BeforeEach
    fun resetStubs() {
        wireMock.resetAll()
    }

    @Test
    fun `importing an OSM node returns 201 Created with the mapped POS fields`() {
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/xml")
                    .withBody(osmXml(NODE_ID, validTags()))
            )
        )

        val result = importNode(MODERATOR).returnResult<PosDto>()

        assertThat(result.status.value()).isEqualTo(HttpStatus.CREATED.value())
        val imported = result.responseBody!!

        assertThat(imported.name).isEqualTo("Campus Cafe")
        assertThat(imported.type).isEqualTo(PosType.CAFE)
        assertThat(imported.postalCode).isEqualTo("69117")
        assertThat(imported.city).isEqualTo("Heidelberg")
        // the location must point at the created POS resource, not at the import URL
        assertThat(result.responseHeaders.location.toString()).endsWith("/api/pos/${imported.id}")
    }

    @Test
    fun `importing the same OSM node twice returns 409 Conflict`() {
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/xml")
                    .withBody(osmXml(NODE_ID, validTags()))
            )
        )

        val firstStatus = importNode(MODERATOR).returnResult<ByteArray>().status.value()
        assertThat(firstStatus).isEqualTo(HttpStatus.CREATED.value())

        // the second import hits the unique POS name; there is no update-by-import
        val secondStatus = importNode(MODERATOR).returnResult<ByteArray>().status.value()
        assertThat(secondStatus).isEqualTo(HttpStatus.CONFLICT.value())
    }

    @Test
    fun `importing while the OSM API fails returns 502 Bad Gateway instead of 404`() {
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID"))
                .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value()))
        )

        val status = importNode(MODERATOR).returnResult<ByteArray>().status.value()

        assertThat(status).isEqualTo(HttpStatus.BAD_GATEWAY.value())
    }

    @Test
    fun `importing a missing OSM node returns 404 Not Found`() {
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value()))
        )

        val status = importNode(MODERATOR).returnResult<ByteArray>().status.value()

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `importing an OSM node with a missing tag returns 400 Bad Request`() {
        val withoutAmenity = validTags().apply { remove("amenity") }
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/xml")
                    .withBody(osmXml(NODE_ID, withoutAmenity))
            )
        )

        val status = importNode(MODERATOR).returnResult<ByteArray>().status.value()

        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `importing an OSM node as a plain USER returns 403 Forbidden`() {
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/xml")
                    .withBody(osmXml(NODE_ID, validTags()))
            )
        )

        // the import is a POS write request, so a user without MODERATOR is forbidden before the OSM call happens
        val status = importNode(USER).returnResult<ByteArray>().status.value()

        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    // helpers ---------------------------------------------------------------------

    /** Imports [NODE_ID] for the INF campus, authenticating with the given credentials. */
    private fun importNode(credentials: Credentials): RestTestClient.ResponseSpec =
        client()
            .post()
            .uri("/api/pos/import/osm/{nodeId}?campus_type={campus}", NODE_ID, "INF")
            .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
            .exchange()

    private fun validTags(): MutableMap<String, String> =
        linkedMapOf(
            "name" to "Campus Cafe",
            "amenity" to "cafe",
            "addr:city" to "Heidelberg",
            "addr:street" to "Hauptstrasse",
            "addr:housenumber" to "5",
            "addr:postcode" to "69117"
        )

    private fun osmXml(
        nodeId: Long,
        tags: Map<String, String>
    ): String =
        buildString {
            append("<osm>\n  <node id=\"").append(nodeId).append("\">\n")
            tags.forEach { (key, value) ->
                append("    <tag k=\"")
                    .append(key)
                    .append("\" v=\"")
                    .append(value)
                    .append("\"/>\n")
            }
            append("  </node>\n</osm>\n")
        }

    private companion object {
        const val NODE_ID = 123L

        private val wireMock = WireMockServer(options().dynamicPort()).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun osmProperties(registry: DynamicPropertyRegistry) {
            registry.add("osm.api.base-url") { "http://localhost:${wireMock.port()}" }
        }

        @JvmStatic
        @AfterAll
        fun stopWireMock() {
            wireMock.stop()
        }
    }
}
