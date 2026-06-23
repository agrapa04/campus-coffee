package de.seuhd.campuscoffee.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.charset.StandardCharsets

/**
 * Web MVC configuration for the REST API: centralizes the base path and keeps responses JSON-only.
 *
 * Every controller in the `api.controller` package is served under [API_BASE_PATH], so the prefix is
 * declared once here instead of repeated on each `@RequestMapping`.
 */
@Configuration
class ApiWebConfig : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(
            API_BASE_PATH,
            HandlerTypePredicate.forBasePackage("de.seuhd.campuscoffee.api.controller")
        )
    }

    /**
     * Keep the REST API JSON-only and UTF-8 explicit.
     *
     * `jackson-dataformat-xml` is on the classpath so the data layer can parse OpenStreetMap responses with
     * its own `XmlMapper`; left in the web layer it also makes content negotiation serve XML whenever a
     * client's `Accept` header prefers it (a browser does), rendering `LocalDateTime` as a clumsy
     * element-per-field structure. Drop any XML converter so responses are JSON.
     *
     * Spring no longer adds a `charset` to JSON responses (UTF-8 is the JSON default per RFC 8259), so the
     * `Content-Type` is a bare `application/json`. Clients that fall back to ISO-8859-1 when no charset is
     * declared then mojibake non-ASCII text (e.g. `Hauptstraße` renders as `HauptstraÃŸe`). Pin the charset
     * back onto the JSON converter so responses advertise `application/json;charset=UTF-8`.
     */
    override fun configureMessageConverters(builder: HttpMessageConverters.ServerBuilder) {
        builder.configureMessageConvertersList { converters ->
            converters.removeIf { converter -> converter.supportedMediaTypes.any { it.subtype.endsWith("xml") } }
            converters
                .filterIsInstance<AbstractHttpMessageConverter<*>>()
                .filter { converter ->
                    converter.supportedMediaTypes.any {
                        it.subtype == "json" ||
                            it.subtype.endsWith("+json")
                    }
                }.forEach { converter ->
                    converter.supportedMediaTypes =
                        converter.supportedMediaTypes.map { MediaType(it.type, it.subtype, StandardCharsets.UTF_8) }
                }
        }
    }

    companion object {
        const val API_BASE_PATH = "/api"
    }
}
