package de.seuhd.campuscoffee.data.implementations;

import de.seuhd.campuscoffee.data.client.OsmFeignClient;
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity;
import de.seuhd.campuscoffee.domain.model.objects.OsmNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

/**
 * Tests how {@link OsmDataServiceImpl} parses the OSM XML response: required tags, amenity resolution,
 * the name fallback, and the failure paths for empty responses and unsupported or missing tags.
 */
@ExtendWith(MockitoExtension.class)
class OsmDataServiceTest {

    private static final long NODE_ID = 123L;

    @Mock
    private OsmFeignClient osmFeignClient;

    private OsmDataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OsmDataServiceImpl(osmFeignClient);
    }

    @Test
    void validResponseIsParsed() {
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, validTags()));

        OsmNode node = service.fetchNode(NODE_ID);

        assertThat(node.nodeId()).isEqualTo(NODE_ID);
        assertThat(node.name()).isEqualTo("Campus Cafe");
        assertThat(node.amenity()).isEqualTo(OsmAmenity.CAFE);
        assertThat(node.city()).isEqualTo("Heidelberg");
        assertThat(node.street()).isEqualTo("Hauptstrasse");
        assertThat(node.houseNumber()).isEqualTo("5");
        assertThat(node.postcode()).isEqualTo("69117");
        // no description tag, so the default applies
        assertThat(node.description()).isEqualTo(OsmDataServiceImpl.DEFAULT_DESCRIPTION);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void emptyOrNullResponseThrowsNotFound(String response) {
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn(response);

        assertThatThrownBy(() -> service.fetchNode(NODE_ID)).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "amenity", "addr:city", "addr:street", "addr:housenumber", "addr:postcode"})
    void missingRequiredTagThrowsMissingField(String missingKey) {
        Map<String, String> tags = validTags();
        tags.remove(missingKey);
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags));

        assertThatThrownBy(() -> service.fetchNode(NODE_ID)).isInstanceOf(MissingFieldException.class);
    }

    @Test
    void unsupportedAmenityThrowsMissingField() {
        Map<String, String> tags = validTags();
        tags.put("amenity", "library");
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags));

        assertThatThrownBy(() -> service.fetchNode(NODE_ID)).isInstanceOf(MissingFieldException.class);
    }

    @Test
    void responseWithoutNodeElementThrowsNotFound() {
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn("<osm></osm>");

        assertThatThrownBy(() -> service.fetchNode(NODE_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void nodeWithoutIdThrowsNotFound() {
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn(
                "<osm>\n  <node>\n    <tag k=\"amenity\" v=\"cafe\"/>\n    <tag k=\"name\" v=\"X\"/>\n  </node>\n</osm>\n");

        assertThatThrownBy(() -> service.fetchNode(NODE_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void singleTagIsTreatedAsNoTagsAndReportsMissingField() {
        // the deserializer only collects tags when they form an array, so a lone tag yields no tags
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn(
                "<osm>\n  <node id=\"" + NODE_ID + "\">\n    <tag k=\"amenity\" v=\"cafe\"/>\n  </node>\n</osm>\n");

        assertThatThrownBy(() -> service.fetchNode(NODE_ID)).isInstanceOf(MissingFieldException.class);
    }

    static Stream<Arguments> nameFallback() {
        return Stream.of(
                arguments(null, null, "Campus Cafe"),                 // only the plain name tag
                arguments("Cafe DE", null, "Cafe DE"),                // name:de wins over name
                arguments(null, "Cafe EN", "Cafe EN"),                // name:en wins over name
                arguments("Cafe DE", "Cafe EN", "Cafe EN")            // name:en wins over name:de
        );
    }

    @ParameterizedTest
    @MethodSource("nameFallback")
    void namePrefersEnglishThenGermanThenPlainName(String nameDe, String nameEn, String expected) {
        Map<String, String> tags = validTags();
        if (nameDe != null) {
            tags.put("name:de", nameDe);
        }
        if (nameEn != null) {
            tags.put("name:en", nameEn);
        }
        when(osmFeignClient.fetchNode(NODE_ID)).thenReturn(xml(NODE_ID, tags));

        assertThat(service.fetchNode(NODE_ID).name()).isEqualTo(expected);
    }

    private Map<String, String> validTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("name", "Campus Cafe");
        tags.put("amenity", "cafe");
        tags.put("addr:city", "Heidelberg");
        tags.put("addr:street", "Hauptstrasse");
        tags.put("addr:housenumber", "5");
        tags.put("addr:postcode", "69117");
        return tags;
    }

    private String xml(long nodeId, Map<String, String> tags) {
        StringBuilder xml = new StringBuilder();
        xml.append("<osm>\n  <node id=\"").append(nodeId).append("\">\n");
        tags.forEach((key, value) ->
                xml.append("    <tag k=\"").append(key).append("\" v=\"").append(value).append("\"/>\n"));
        xml.append("  </node>\n</osm>\n");
        return xml.toString();
    }
}
