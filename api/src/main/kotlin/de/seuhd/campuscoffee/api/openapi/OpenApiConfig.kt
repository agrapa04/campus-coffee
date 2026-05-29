package de.seuhd.campuscoffee.api.openapi

import de.seuhd.campuscoffee.api.exceptions.ErrorResponse
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI/Swagger configuration: global API metadata and registration of the ErrorResponse schema.
 */
@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "CampusCoffee API",
        version = "0.0.5",
        description = "REST API for managing campus coffee points of sale, users, and reviews.",
    ),
)
class OpenApiConfig {
    /**
     * Registers the ErrorResponse schema in the OpenAPI components. It is only referenced
     * programmatically (by the custom CRUD annotations), so it would otherwise be absent from the
     * Swagger UI schema list.
     */
    @Bean
    fun errorResponseSchemaCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        ModelConverters.getInstance().read(ErrorResponse::class.java)
            .forEach { (name, schema) -> openApi.components.addSchemas(name, schema) }
    }
}
