package de.seuhd.campuscoffee.tests.system;

import com.github.tomakehurst.wiremock.WireMockServer;
import de.seuhd.campuscoffee.api.dtos.PosDto;
import de.seuhd.campuscoffee.domain.model.enums.PosType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for importing a POS from an OpenStreetMap node. The external OSM HTTP API is stubbed
 * with WireMock, and the Feign client is pointed at the stub through the {@code osm.api.base-url}
 * property. The server starts before the Spring context so the Feign client resolves the stub URL.
 */
public class OsmImportSystemTests extends AbstractSysTest {

    private static final long NODE_ID = 123L;

    private static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void osmProperties(DynamicPropertyRegistry registry) {
        registry.add("osm.api.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void importOsmNodeCreatesPos() {
        wireMock.stubFor(get(urlEqualTo("/node/" + NODE_ID))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/xml")
                        .withBody(osmXml(NODE_ID, validTags()))));

        PosDto imported = given()
                .queryParam("campus_type", "INF")
                .when()
                .post("/api/pos/import/osm/{nodeId}", NODE_ID)
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .extract().as(PosDto.class);

        assertThat(imported.name()).isEqualTo("Campus Cafe");
        assertThat(imported.type()).isEqualTo(PosType.CAFE);
        assertThat(imported.postalCode()).isEqualTo(69117);
        assertThat(imported.city()).isEqualTo("Heidelberg");
    }

    @Test
    void importMissingOsmNodeReturnsNotFound() {
        wireMock.stubFor(get(urlEqualTo("/node/" + NODE_ID))
                .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

        given()
                .queryParam("campus_type", "INF")
                .when()
                .post("/api/pos/import/osm/{nodeId}", NODE_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void importOsmNodeWithMissingTagReturnsBadRequest() {
        Map<String, String> withoutAmenity = validTags();
        withoutAmenity.remove("amenity");
        wireMock.stubFor(get(urlEqualTo("/node/" + NODE_ID))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/xml")
                        .withBody(osmXml(NODE_ID, withoutAmenity))));

        given()
                .queryParam("campus_type", "INF")
                .when()
                .post("/api/pos/import/osm/{nodeId}", NODE_ID)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    // helpers ---------------------------------------------------------------------

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

    private String osmXml(long nodeId, Map<String, String> tags) {
        StringBuilder xml = new StringBuilder();
        xml.append("<osm>\n  <node id=\"").append(nodeId).append("\">\n");
        tags.forEach((key, value) ->
                xml.append("    <tag k=\"").append(key).append("\" v=\"").append(value).append("\"/>\n"));
        xml.append("  </node>\n</osm>\n");
        return xml.toString();
    }
}
