package de.seuhd.campuscoffee.data.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * Builds the [OsmClient] proxy from a RestClient pointed at the OSM API, with the User-Agent header
 * the API requires.
 */
@Configuration
class OsmClientConfig {
    @Bean
    fun osmClient(
        @Value("\${osm.api.base-url}") baseUrl: String
    ): OsmClient {
        val restClient =
            RestClient
                .builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "CampusCoffee/0.0.2")
                .build()
        val factory =
            HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
        return factory.createClient(OsmClient::class.java)
    }
}
