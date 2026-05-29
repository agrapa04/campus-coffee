package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity
import de.seuhd.campuscoffee.domain.model.enums.PosType
import de.seuhd.campuscoffee.domain.model.objects.OsmNode
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Service
class PosServiceImpl(
    private val posDataService: PosDataService,
    private val osmDataService: OsmDataService
) : CrudServiceImpl<Pos, Long>(Pos::class.java),
    PosService {
    override fun dataService(): CrudDataService<Pos, Long> = posDataService

    override fun getByName(name: String): Pos {
        log.debug("Retrieving POS with name: {}", name)
        return posDataService.getByName(name)
    }

    @Transactional
    override fun importFromOsmNode(
        nodeId: Long,
        campusType: CampusType
    ): Pos {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId)

        // fetch the OSM node data using the port
        val osmNode = osmDataService.fetchNode(nodeId)

        // convert OSM node to POS domain object and upsert it
        val savedPos = upsert(convertOsmNodeToPos(osmNode, campusType))
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name, nodeId)

        return savedPos
    }

    /**
     * Converts an OSM node to a POS domain object, mapping OSM amenity types to POS types and
     * validating required fields.
     *
     * @throws MissingFieldException if required fields are missing or invalid
     */
    private fun convertOsmNodeToPos(
        osmNode: OsmNode,
        campusType: CampusType
    ): Pos {
        val posType = mapAmenityToPosType(osmNode.amenity)

        val postalCode =
            try {
                osmNode.postcode.toInt()
            } catch (e: NumberFormatException) {
                log.error("Could not parse postcode {} of OSM node {}", osmNode.postcode, osmNode.nodeId)
                throw MissingFieldException(OsmNode::class.java, osmNode.nodeId, "postcode")
            }

        return Pos(
            name = osmNode.name,
            description = osmNode.description,
            type = posType,
            campus = campusType,
            street = osmNode.street,
            houseNumber = osmNode.houseNumber,
            postalCode = postalCode,
            city = osmNode.city
        )
    }

    /**
     * Maps an OpenStreetMap amenity type to a POS type.
     */
    private fun mapAmenityToPosType(amenity: OsmAmenity): PosType =
        when (amenity) {
            OsmAmenity.CAFE, OsmAmenity.ICE_CREAM -> PosType.CAFE
            OsmAmenity.VENDING_MACHINE -> PosType.VENDING_MACHINE
            OsmAmenity.FOOD_COURT -> PosType.CAFETERIA
            OsmAmenity.BAR, OsmAmenity.BIERGARTEN, OsmAmenity.PUB, OsmAmenity.RESTAURANT,
            OsmAmenity.FAST_FOOD -> PosType.OTHER
        }

    private companion object {
        private val log = LoggerFactory.getLogger(PosServiceImpl::class.java)
    }
}
