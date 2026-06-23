package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.client.OsmClient
import de.seuhd.campuscoffee.domain.exceptions.ExternalServiceException
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.IOException
import java.util.stream.Stream

/**
 * Tests how [OsmDataServiceImpl] parses the OSM XML response: required tags, amenity resolution, the
 * name fallback, and the failure paths — a missing node is a [NotFoundException], while transport
 * errors, server errors, and malformed responses are [ExternalServiceException]s.
 */
@ExtendWith(MockitoExtension::class)
class OsmDataServiceTest {
    @Mock
    private lateinit var osmClient: OsmClient

    private lateinit var service: OsmDataServiceImpl

    @BeforeEach
    fun setUp() {
        service = OsmDataServiceImpl(osmClient)
    }

    @Test
    fun `fetchNode parses a valid OSM response into a node`() {
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, validTags()))

        val node = service.fetchNode(NODE_ID)

        assertThat(node.nodeId).isEqualTo(NODE_ID)
        assertThat(node.name).isEqualTo("Campus Cafe")
        assertThat(node.amenity).isEqualTo(OsmAmenity.CAFE)
        assertThat(node.city).isEqualTo("Heidelberg")
        assertThat(node.street).isEqualTo("Hauptstrasse")
        assertThat(node.houseNumber).isEqualTo("5")
        assertThat(node.postcode).isEqualTo("69117")
        // no description tag, so the default applies
        assertThat(node.description).isEqualTo(OsmDataServiceImpl.DEFAULT_DESCRIPTION)
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `fetchNode throws ExternalServiceException for an empty or null response`(response: String?) {
        // a 2xx without a body is a malformed OSM response, not a missing node (that would be a 404)
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(response)

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["name", "amenity", "addr:city", "addr:street", "addr:housenumber", "addr:postcode"])
    fun `fetchNode throws MissingFieldException when a required tag is missing`(missingKey: String) {
        val tags = validTags()
        tags.remove(missingKey)
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags))

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(MissingFieldException::class.java)
    }

    @Test
    fun `fetchNode throws MissingFieldException for an unsupported amenity`() {
        val tags = validTags()
        tags["amenity"] = "library"
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags))

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(MissingFieldException::class.java)
    }

    @Test
    fun `fetchNode throws ExternalServiceException when the response has no node element`() {
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn("<osm></osm>")

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @Test
    fun `fetchNode throws ExternalServiceException when the node has no id`() {
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(
            "<osm>\n  <node>\n    <tag k=\"amenity\" v=\"cafe\"/>\n    <tag k=\"name\" v=\"X\"/>\n  </node>\n</osm>\n"
        )

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @Test
    fun `fetchNode throws ExternalServiceException for unparseable XML`() {
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn("this is not XML")

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @Test
    fun `fetchNode throws NotFoundException when the OSM API answers 404 Not Found`() {
        whenever(osmClient.fetchNode(NODE_ID)).thenThrow(httpClientError(HttpStatus.NOT_FOUND))

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `fetchNode throws NotFoundException when the OSM API answers 410 Gone for a deleted node`() {
        whenever(osmClient.fetchNode(NODE_ID)).thenThrow(httpClientError(HttpStatus.GONE))

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `fetchNode throws ExternalServiceException when the OSM API answers with a server error`() {
        whenever(osmClient.fetchNode(NODE_ID)).thenThrow(
            HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                HttpHeaders.EMPTY,
                ByteArray(0),
                null
            )
        )

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @Test
    fun `fetchNode throws ExternalServiceException for a transport error such as a timeout`() {
        whenever(osmClient.fetchNode(NODE_ID))
            .thenThrow(ResourceAccessException("I/O error", IOException("connection timed out")))

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @Test
    fun `fetchNode parses a lone tag but still throws MissingFieldException for the other required tags`() {
        // a single <tag> deserializes as an object, not an array; it is now collected (so the lone amenity is
        // read), but the node still lacks the other required tags, so a MissingFieldException is raised
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(
            "<osm>\n  <node id=\"$NODE_ID\">\n    <tag k=\"amenity\" v=\"cafe\"/>\n  </node>\n</osm>\n"
        )

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(MissingFieldException::class.java)
    }

    @Test
    fun `fetchNode throws ExternalServiceException when a tag is missing its value attribute`() {
        // a syntactically valid tag carrying only k (no v) is a malformed OSM response, mapped to a 502
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(
            "<osm>\n  <node id=\"$NODE_ID\">\n    <tag k=\"amenity\" v=\"cafe\"/>\n    <tag k=\"name\"/>\n  </node>\n</osm>\n"
        )

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @Test
    fun `fetchNode throws ExternalServiceException when a tag is missing its key attribute`() {
        // symmetric to the missing-value case: a tag carrying only v (no k) is also a malformed response
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(
            "<osm>\n  <node id=\"$NODE_ID\">\n    <tag k=\"amenity\" v=\"cafe\"/>\n    <tag v=\"X\"/>\n  </node>\n</osm>\n"
        )

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(ExternalServiceException::class.java)
    }

    @ParameterizedTest
    @MethodSource("nameFallback")
    fun `fetchNode prefers the English then German then plain name`(
        nameDe: String?,
        nameEn: String?,
        expected: String
    ) {
        val tags = validTags()
        if (nameDe != null) tags["name:de"] = nameDe
        if (nameEn != null) tags["name:en"] = nameEn
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags))

        assertThat(service.fetchNode(NODE_ID).name).isEqualTo(expected)
    }

    private fun validTags(): MutableMap<String, String> =
        linkedMapOf(
            "name" to "Campus Cafe",
            "amenity" to "cafe",
            "addr:city" to "Heidelberg",
            "addr:street" to "Hauptstrasse",
            "addr:housenumber" to "5",
            "addr:postcode" to "69117"
        )

    private fun xml(
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

    private fun httpClientError(status: HttpStatus): HttpClientErrorException =
        HttpClientErrorException.create(status, status.reasonPhrase, HttpHeaders.EMPTY, ByteArray(0), null)

    companion object {
        private const val NODE_ID = 123L

        @JvmStatic
        fun nameFallback(): Stream<Arguments> =
            Stream.of(
                arguments(null, null, "Campus Cafe"), // only the plain name tag
                arguments("Cafe DE", null, "Cafe DE"), // name:de wins over name
                arguments(null, "Cafe EN", "Cafe EN"), // name:en wins over name
                arguments("Cafe DE", "Cafe EN", "Cafe EN") // name:en wins over name:de
            )
    }
}
