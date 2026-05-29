package de.seuhd.campuscoffee.data.client

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange

/**
 * Declarative HTTP client for the OpenStreetMap API.
 */
interface OsmClient {
    /**
     * Fetches a node by its ID from the OpenStreetMap API.
     *
     * @param nodeId the OSM node ID
     * @return the XML response body, or null if the response has no body
     */
    @GetExchange("/node/{id}")
    fun fetchNode(@PathVariable("id") nodeId: Long): String?
}
