package de.seuhd.campuscoffee.data.implementations;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import de.seuhd.campuscoffee.data.client.OsmFeignClient;
import de.seuhd.campuscoffee.data.client.OsmResponse;
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity;
import de.seuhd.campuscoffee.domain.model.objects.OsmNode;
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * OSM data service that fetches node data from the OpenStreetMap API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class OsmDataServiceImpl implements OsmDataService {
    /** Description applied when an OSM node carries no {@code description} tag. */
    static final String DEFAULT_DESCRIPTION = "n/a";

    private final OsmFeignClient osmFeignClient;

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) {
        try {
            log.debug("Fetching OSM node with ID '{}'...", nodeId);
            String xmlResponse = osmFeignClient.fetchNode(nodeId);

            if (xmlResponse == null || xmlResponse.isEmpty()) {
                log.error("Empty response from OSM API for node with ID '{}'.", nodeId);
                throw new NotFoundException(OsmNode.class, nodeId);
            }

            OsmNode node = parseOsmXml(xmlResponse, nodeId);

            log.debug("Successfully fetched and parsed OSM node with ID '{}'.", nodeId);
            return node;

        } catch (FeignException.NotFound e) {
            log.warn("OSM node with ID '{}' not found.", nodeId);
            throw new NotFoundException(OsmNode.class, nodeId);
        } catch (FeignException e) {
            log.error("HTTP error fetching OSM node with ID '{}': {} - {}",
                    nodeId, e.status(), e.getMessage());
            throw new NotFoundException(OsmNode.class, nodeId);
        } catch (MissingFieldException e) {
            // re-throw missing fields exception as-is
            throw e;
        } catch (Exception e) {
            log.error("Error fetching OSM node with ID '{}'", nodeId, e);
            throw new NotFoundException(OsmNode.class, nodeId);
        }
    }

    /**
     * Parses the OSM XML response and extracts node data.
     *
     * @param xmlResponse the XML response from OSM API
     * @param nodeId the node ID for error reporting
     * @return parsed OsmNode object
     * @throws IOException if XML parsing fails
     * @throws MissingFieldException if required fields are missing
     */
    private OsmNode parseOsmXml(String xmlResponse, Long nodeId) throws IOException {
        // parse XML using Jackson (deserializer ensures node element and id are present)
        XmlMapper xmlMapper = new XmlMapper();
        OsmResponse osmResponse = xmlMapper.readValue(xmlResponse, OsmResponse.class);
        Map<String, String> tags = osmResponse.getTags();

        // extract required fields
        String name = getRequiredTag(tags, "name", nodeId);
        String city = getRequiredTag(tags, "addr:city", nodeId);
        String street = getRequiredTag(tags, "addr:street", nodeId);
        String houseNumber = getRequiredTag(tags, "addr:housenumber", nodeId);
        String postcode = getRequiredTag(tags, "addr:postcode", nodeId);
        String amenityStr = getRequiredTag(tags, "amenity", nodeId);
        OsmAmenity amenity = OsmAmenity.fromOsmValue(amenityStr)
                .orElseThrow(() -> {
                    log.warn("OSM node {} has unsupported amenity type: {}", nodeId, amenityStr);
                    return new MissingFieldException(OsmNode.class, nodeId, "amenity");
                });

        // extract optional fields
        Optional<String> nameDe = Optional.ofNullable(tags.get("name:de"));
        Optional<String> nameEn = Optional.ofNullable(tags.get("name:en"));
        Optional<String> description = Optional.ofNullable(tags.get("description"));

        // build and return the OsmNode
        return OsmNode.builder()
                .nodeId(nodeId)
                .name(nameEn.or(() -> nameDe).orElse(name)) // prioritize nameEn, then nameDe, then fall back to name
                .amenity(amenity)
                .city(city)
                .street(street)
                .houseNumber(houseNumber)
                .postcode(postcode)
                .description(description.orElse(DEFAULT_DESCRIPTION))
                .build();
    }

    /**
     * Retrieves a required tag from the tag map.
     *
     * @param tags   the map of OSM tags
     * @param key    the tag key to retrieve
     * @param nodeId the node ID for error reporting
     * @return the tag value
     * @throws MissingFieldException if the tag is missing
     */
    private String getRequiredTag(Map<String, String> tags, String key, Long nodeId) {
        return Optional.ofNullable(tags.get(key))
                .orElseThrow(() -> {
                    log.warn("OSM node {} is missing required field: '{}'. Available tags: {}",
                            nodeId, key, tags.keySet());
                    return new MissingFieldException(OsmNode.class, nodeId, key);
                });
    }
}
