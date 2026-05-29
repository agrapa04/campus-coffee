package de.seuhd.campuscoffee.data.client

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the OpenStreetMap API client: the base URL of the OSM REST API.
 */
@ConfigurationProperties("osm.api")
data class OsmApiProperties(
    val baseUrl: String
)
