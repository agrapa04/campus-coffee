package de.seuhd.campuscoffee.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC configuration for the REST API: centralizes the base path and keeps responses JSON-only.
 *
 * Every controller in the `api.controller` package is served under [API_BASE_PATH], so the prefix is
 * declared once here instead of repeated on each `@RequestMapping`.
 */
@Configuration
class ApiPathConfig : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(
            API_BASE_PATH,
            HandlerTypePredicate.forBasePackage("de.seuhd.campuscoffee.api.controller")
        )
    }

    /**
     * Keep the REST API JSON-only. `jackson-dataformat-xml` is on the classpath so the data layer can
     * parse OpenStreetMap responses with its own `XmlMapper`; left in the web layer it also makes content
     * negotiation serve XML whenever a client's `Accept` header prefers it (a browser does), rendering
     * `LocalDateTime` as a clumsy element-per-field structure. Drop any XML converter so responses are JSON.
     */
    override fun configureMessageConverters(builder: HttpMessageConverters.ServerBuilder) {
        builder.configureMessageConvertersList { converters ->
            converters.removeIf { converter -> converter.supportedMediaTypes.any { it.subtype.endsWith("xml") } }
        }
    }

    companion object {
        const val API_BASE_PATH = "/api"
    }
}
