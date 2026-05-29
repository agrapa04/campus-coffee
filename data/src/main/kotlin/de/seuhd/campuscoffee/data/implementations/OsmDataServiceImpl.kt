package de.seuhd.campuscoffee.data.implementations

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import de.seuhd.campuscoffee.data.client.OsmClient
import de.seuhd.campuscoffee.data.client.OsmResponse
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity
import de.seuhd.campuscoffee.domain.model.objects.OsmNode
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.util.Optional

/**
 * OSM data service that fetches node data from the OpenStreetMap API.
 */
@Service
class OsmDataServiceImpl(
    private val osmClient: OsmClient,
) : OsmDataService {

    override fun fetchNode(nodeId: Long): OsmNode {
        try {
            log.debug("Fetching OSM node with ID '{}'...", nodeId)
            val xmlResponse = osmClient.fetchNode(nodeId)

            if (xmlResponse.isNullOrEmpty()) {
                log.error("Empty response from OSM API for node with ID '{}'.", nodeId)
                throw NotFoundException(OsmNode::class.java, nodeId)
            }

            val node = parseOsmXml(xmlResponse, nodeId)
            log.debug("Successfully fetched and parsed OSM node with ID '{}'.", nodeId)
            return node
        } catch (e: RestClientException) {
            log.warn("HTTP error fetching OSM node with ID '{}': {}", nodeId, e.message)
            throw NotFoundException(OsmNode::class.java, nodeId)
        } catch (e: MissingFieldException) {
            // re-throw missing fields exception as-is
            throw e
        } catch (e: Exception) {
            log.error("Error fetching OSM node with ID '{}'", nodeId, e)
            throw NotFoundException(OsmNode::class.java, nodeId)
        }
    }

    /**
     * Parses the OSM XML response and extracts node data.
     *
     * @throws MissingFieldException if required fields are missing
     */
    private fun parseOsmXml(xmlResponse: String, nodeId: Long): OsmNode {
        // parse XML using Jackson (the deserializer ensures the node element and id are present)
        val osmResponse = XmlMapper().readValue(xmlResponse, OsmResponse::class.java)
        val tags = osmResponse.tags

        val name = getRequiredTag(tags, "name", nodeId)
        val city = getRequiredTag(tags, "addr:city", nodeId)
        val street = getRequiredTag(tags, "addr:street", nodeId)
        val houseNumber = getRequiredTag(tags, "addr:housenumber", nodeId)
        val postcode = getRequiredTag(tags, "addr:postcode", nodeId)
        val amenityStr = getRequiredTag(tags, "amenity", nodeId)
        val amenity = OsmAmenity.fromOsmValue(amenityStr).orElseThrow {
            log.warn("OSM node {} has unsupported amenity type: {}", nodeId, amenityStr)
            MissingFieldException(OsmNode::class.java, nodeId, "amenity")
        }

        val nameDe = Optional.ofNullable(tags["name:de"])
        val nameEn = Optional.ofNullable(tags["name:en"])
        val description = Optional.ofNullable(tags["description"])

        return OsmNode(
            nodeId = nodeId,
            // prioritize nameEn, then nameDe, then fall back to name
            name = nameEn.or { nameDe }.orElse(name),
            amenity = amenity,
            city = city,
            street = street,
            houseNumber = houseNumber,
            postcode = postcode,
            description = description.orElse(DEFAULT_DESCRIPTION),
        )
    }

    /**
     * Retrieves a required tag from the tag map.
     *
     * @throws MissingFieldException if the tag is missing
     */
    private fun getRequiredTag(tags: Map<String, String>, key: String, nodeId: Long): String =
        Optional.ofNullable(tags[key]).orElseThrow {
            log.warn(
                "OSM node {} is missing required field: '{}'. Available tags: {}",
                nodeId, key, tags.keys,
            )
            MissingFieldException(OsmNode::class.java, nodeId, key)
        }

    companion object {
        private val log = LoggerFactory.getLogger(OsmDataServiceImpl::class.java)

        /** Description applied when an OSM node carries no `description` tag. */
        const val DEFAULT_DESCRIPTION: String = "n/a"
    }
}
