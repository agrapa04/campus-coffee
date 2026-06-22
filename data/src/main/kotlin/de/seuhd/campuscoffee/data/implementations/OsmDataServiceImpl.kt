package de.seuhd.campuscoffee.data.implementations

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import de.seuhd.campuscoffee.data.client.OsmClient
import de.seuhd.campuscoffee.data.client.OsmResponse
import de.seuhd.campuscoffee.domain.exceptions.ExternalServiceException
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity
import de.seuhd.campuscoffee.domain.model.objects.OsmNode
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException

/**
 * OSM data service that fetches node data from the OpenStreetMap API.
 *
 * Failure modes are kept distinct: only a 404/410 from the OSM API means the node does not exist;
 * transport errors, timeouts, server errors, and malformed or empty responses are failures of the
 * external service
 * ([ExternalServiceException]), so an OSM outage is not misreported as a missing node.
 */
@Service
class OsmDataServiceImpl(
    private val osmClient: OsmClient
) : OsmDataService {
    override fun fetchNode(nodeId: Long): OsmNode {
        log.debug { "Fetching OSM node with ID '$nodeId'..." }
        val xmlResponse =
            try {
                osmClient.fetchNode(nodeId)
            } catch (ignored: HttpClientErrorException.NotFound) {
                // a 404 conveys nothing beyond its status code, so the exception is not kept as a cause
                log.warn { "OSM node with ID '$nodeId' does not exist." }
                throw NotFoundException(OsmNode::class.java, nodeId)
            } catch (ignored: HttpClientErrorException.Gone) {
                // the OSM API reports deleted nodes as 410 Gone
                log.warn { "OSM node with ID '$nodeId' was deleted." }
                throw NotFoundException(OsmNode::class.java, nodeId)
            } catch (e: RestClientException) {
                log.error(e) { "Error calling the OSM API for node with ID '$nodeId'" }
                throw ExternalServiceException(OSM_SERVICE_NAME, e)
            }

        if (xmlResponse.isNullOrEmpty()) {
            // a 2xx without a body is a malformed OSM response (a missing node would have been a 404)
            log.error { "Empty response from OSM API for node with ID '$nodeId'." }
            throw ExternalServiceException(OSM_SERVICE_NAME)
        }

        val node =
            try {
                parseOsmXml(xmlResponse, nodeId)
            } catch (e: JacksonException) {
                log.error(e) { "Malformed XML from OSM API for node with ID '$nodeId'" }
                throw ExternalServiceException(OSM_SERVICE_NAME, e)
            }
        log.debug { "Successfully fetched and parsed OSM node with ID '$nodeId'." }
        return node
    }

    /**
     * Parses the OSM XML response and extracts node data.
     *
     * @throws MissingFieldException if required fields are missing
     */
    private fun parseOsmXml(
        xmlResponse: String,
        nodeId: Long
    ): OsmNode {
        // parse XML using Jackson (the deserializer ensures the node element and id are present)
        val osmResponse = XmlMapper().readValue(xmlResponse, OsmResponse::class.java)
        val tags = osmResponse.tags

        val name = getRequiredTag(tags, "name", nodeId)
        val city = getRequiredTag(tags, "addr:city", nodeId)
        val street = getRequiredTag(tags, "addr:street", nodeId)
        val houseNumber = getRequiredTag(tags, "addr:housenumber", nodeId)
        val postcode = getRequiredTag(tags, "addr:postcode", nodeId)
        val amenityStr = getRequiredTag(tags, "amenity", nodeId)
        val amenity =
            OsmAmenity.fromOsmValue(amenityStr) ?: run {
                log.warn { "OSM node $nodeId has unsupported amenity type: $amenityStr" }
                throw MissingFieldException(OsmNode::class.java, nodeId, "amenity")
            }

        val nameDe = tags["name:de"]
        val nameEn = tags["name:en"]
        val description = tags["description"]

        return OsmNode(
            nodeId = nodeId,
            // prioritize nameEn, then nameDe, then fall back to name
            name = nameEn ?: nameDe ?: name,
            amenity = amenity,
            city = city,
            street = street,
            houseNumber = houseNumber,
            postcode = postcode,
            description = description ?: DEFAULT_DESCRIPTION
        )
    }

    /**
     * Retrieves a required tag from the tag map.
     *
     * @throws MissingFieldException if the tag is missing
     */
    private fun getRequiredTag(
        tags: Map<String, String>,
        key: String,
        nodeId: Long
    ): String =
        tags[key] ?: run {
            log.warn { "OSM node $nodeId is missing required field: '$key'. Available tags: ${tags.keys}" }
            throw MissingFieldException(OsmNode::class.java, nodeId, key)
        }

    companion object {
        private val log = KotlinLogging.logger {}

        /** Description applied when an OSM node carries no `description` tag. */
        const val DEFAULT_DESCRIPTION: String = "n/a"

        /** Service name reported in [ExternalServiceException] messages. */
        const val OSM_SERVICE_NAME: String = "OpenStreetMap"
    }
}
