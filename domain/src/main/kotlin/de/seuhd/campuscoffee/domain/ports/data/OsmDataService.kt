package de.seuhd.campuscoffee.domain.ports.data

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.OsmNode

/**
 * Port for importing Point of Sale data from OpenStreetMap.
 *
 * Defines the contract for fetching OSM node data; implementations handle the external
 * API communication.
 */
interface OsmDataService {
    /**
     * Fetches an OpenStreetMap node by its ID.
     *
     * @param nodeId the OpenStreetMap node ID to fetch
     * @return the OSM node data with tags
     * @throws NotFoundException if the node does not exist or cannot be fetched
     */
    fun fetchNode(nodeId: Long): OsmNode
}
