package de.seuhd.campuscoffee.data.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration for the OpenStreetMap API client: the base URL of the OSM REST API and the HTTP
 * timeouts. Without timeouts a hung OSM API would block servlet threads indefinitely.
 *
 * @property baseUrl the base URL of the OpenStreetMap REST API.
 * @property connectTimeout time to wait for the TCP connection to the OSM API (default 5s).
 * @property readTimeout time to wait for the OSM API's response (default 10s).
 */
@ConfigurationProperties("campus-coffee.osm.api")
data class OsmApiProperties(
    val baseUrl: String,
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT
) {
    companion object {
        /** Default time to wait for the TCP connection to the OSM API. */
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)

        /** Default time to wait for the OSM API's response. */
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
