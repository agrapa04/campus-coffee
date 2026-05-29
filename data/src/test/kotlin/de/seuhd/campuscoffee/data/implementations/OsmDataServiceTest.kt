package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.client.OsmClient
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
import java.util.stream.Stream

/**
 * Tests how [OsmDataServiceImpl] parses the OSM XML response: required tags, amenity resolution, the
 * name fallback, and the failure paths for empty responses and unsupported or missing tags.
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
    fun validResponseIsParsed() {
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
    fun emptyOrNullResponseThrowsNotFound(response: String?) {
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(response)

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(NotFoundException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["name", "amenity", "addr:city", "addr:street", "addr:housenumber", "addr:postcode"])
    fun missingRequiredTagThrowsMissingField(missingKey: String) {
        val tags = validTags()
        tags.remove(missingKey)
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags))

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(MissingFieldException::class.java)
    }

    @Test
    fun unsupportedAmenityThrowsMissingField() {
        val tags = validTags()
        tags["amenity"] = "library"
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags))

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(MissingFieldException::class.java)
    }

    @Test
    fun responseWithoutNodeElementThrowsNotFound() {
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn("<osm></osm>")

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun nodeWithoutIdThrowsNotFound() {
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(
            "<osm>\n  <node>\n    <tag k=\"amenity\" v=\"cafe\"/>\n    <tag k=\"name\" v=\"X\"/>\n  </node>\n</osm>\n"
        )

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun singleTagIsTreatedAsNoTagsAndReportsMissingField() {
        // the deserializer only collects tags when they form an array, so a lone tag yields no tags
        whenever(osmClient.fetchNode(NODE_ID)).thenReturn(
            "<osm>\n  <node id=\"$NODE_ID\">\n    <tag k=\"amenity\" v=\"cafe\"/>\n  </node>\n</osm>\n"
        )

        assertThatThrownBy { service.fetchNode(NODE_ID) }.isInstanceOf(MissingFieldException::class.java)
    }

    @ParameterizedTest
    @MethodSource("nameFallback")
    fun namePrefersEnglishThenGermanThenPlainName(
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
