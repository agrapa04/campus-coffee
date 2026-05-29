package de.seuhd.campuscoffee.tests.system

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.domain.model.enums.PosType
import de.seuhd.campuscoffee.tests.SystemTestUtils.client
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * System tests for importing a POS from an OpenStreetMap node. The external OSM HTTP API is stubbed
 * with WireMock, and the OSM client is pointed at the stub through the `osm.api.base-url` property.
 * The server starts before the Spring context so the client resolves the stub URL.
 */
class OsmImportSystemTests : AbstractSysTest() {
    @BeforeEach
    fun resetStubs() {
        wireMock.resetAll()
    }

    @Test
    fun importOsmNodeCreatesPos() {
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/xml")
                    .withBody(osmXml(NODE_ID, validTags()))
            )
        )

        val result =
            client()
                .post()
                .uri("/api/pos/import/osm/{nodeId}?campus_type={campus}", NODE_ID, "INF")
                .exchange()
                .returnResult(PosDto::class.java)

        assertThat(result.status.value()).isEqualTo(HttpStatus.CREATED.value())
        val imported = result.responseBody!!

        assertThat(imported.name).isEqualTo("Campus Cafe")
        assertThat(imported.type).isEqualTo(PosType.CAFE)
        assertThat(imported.postalCode).isEqualTo(69117)
        assertThat(imported.city).isEqualTo("Heidelberg")
    }

    @Test
    fun importMissingOsmNodeReturnsNotFound() {
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value()))
        )

        val status =
            client()
                .post()
                .uri("/api/pos/import/osm/{nodeId}?campus_type={campus}", NODE_ID, "INF")
                .exchange()
                .returnResult(ByteArray::class.java)
                .status
                .value()

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun importOsmNodeWithMissingTagReturnsBadRequest() {
        val withoutAmenity = validTags().apply { remove("amenity") }
        wireMock.stubFor(
            get(urlEqualTo("/node/$NODE_ID")).willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/xml")
                    .withBody(osmXml(NODE_ID, withoutAmenity))
            )
        )

        val status =
            client()
                .post()
                .uri("/api/pos/import/osm/{nodeId}?campus_type={campus}", NODE_ID, "INF")
                .exchange()
                .returnResult(ByteArray::class.java)
                .status
                .value()

        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    // helpers ---------------------------------------------------------------------

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
