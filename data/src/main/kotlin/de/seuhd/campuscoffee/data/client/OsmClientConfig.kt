package de.seuhd.campuscoffee.data.client
import de.seuhd.campuscoffee.data.configuration.OsmApiProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient
import java.net.http.HttpClient

/**
 * Builds the [OsmClient] proxy from a RestClient pointed at the OSM API, with the User-Agent header
 * the API requires and explicit connect/read timeouts (the JDK HttpClient defaults to none, which
 * would let a hung OSM API pin a servlet thread indefinitely).
 */
@Configuration
class OsmClientConfig {
    @Bean
    fun osmClient(
        properties: OsmApiProperties,
        buildProperties: ObjectProvider<BuildProperties>
    ): OsmClient {
        // Version from the build (BuildProperties); "dev" when build-info is absent, e.g., an IDE run.
        val version = buildProperties.ifAvailable?.version ?: "dev"
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(properties.connectTimeout)
                .build()
        val requestFactory =
            JdkClientHttpRequestFactory(httpClient).apply {
                setReadTimeout(properties.readTimeout)
            }
        val restClient =
            RestClient
                .builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl)
                .defaultHeader("User-Agent", "CampusCoffee/$version")
                .build()
        val factory =
            HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
        return factory.createClient<OsmClient>()
    }
}
