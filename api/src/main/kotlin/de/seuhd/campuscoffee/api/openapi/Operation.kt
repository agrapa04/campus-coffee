package de.seuhd.campuscoffee.api.openapi

import org.springframework.http.HttpStatus

/**
 * Supported CRUD operation types, each with its OpenAPI summary template and the response
 * specifications (status codes, description templates, and which are error responses).
 */
enum class Operation(
    val summaryTemplate: (Parameters) -> String,
    val responseSpecifications: List<CrudResponseSpecification>
) {
    GET_ALL(
        { params -> "Get all ${params.resourceName}." },
        listOf(
            CrudResponseSpecification(HttpStatus.OK, "All %s as a JSON array.")
        )
    ),
    GET_BY_ID(
        { params -> "Get ${params.resourceName} by ID." },
        listOf(
            CrudResponseSpecification(HttpStatus.OK, "The %s with the provided ID as a JSON object."),
            CrudResponseSpecification(
                HttpStatus.NOT_FOUND,
                "No %s with the provided ID could be found.",
                isErrorResponse = true
            )
        )
    ),
    CREATE(
        { params -> "Create a new ${params.resourceName}." },
        listOf(
            CrudResponseSpecification(HttpStatus.CREATED, "The new %s as a JSON object."),
            CrudResponseSpecification(
                HttpStatus.BAD_REQUEST,
                "Validation failed (e.g., bean validation error).",
                isErrorResponse = true
            ),
            CrudResponseSpecification(
                HttpStatus.CONFLICT,
                "A %s with the same value for a unique field already exists.",
                isErrorResponse = true
            )
        )
    ),
    UPDATE(
        { params -> "Update ${params.resourceName} by ID." },
        listOf(
            CrudResponseSpecification(HttpStatus.OK, "The updated %s as a JSON object."),
            CrudResponseSpecification(
                HttpStatus.BAD_REQUEST,
                "Validation failed (e.g., IDs in path and body do not match, bean validation error).",
                isErrorResponse = true
            ),
            CrudResponseSpecification(
                HttpStatus.NOT_FOUND,
                "No %s with the provided ID could be found.",
                isErrorResponse = true
            ),
            CrudResponseSpecification(
                HttpStatus.CONFLICT,
                "A %s with the same unique identifier already exists.",
                isErrorResponse = true
            )
        )
    ),
    DELETE(
        { params -> "Delete ${params.resourceName} by ID." },
        listOf(
            CrudResponseSpecification(HttpStatus.NO_CONTENT, "The %s was successfully deleted."),
            CrudResponseSpecification(
                HttpStatus.NOT_FOUND,
                "No %s with the provided ID could be found.",
                isErrorResponse = true
            )
        )
    ),
    FILTER(
        { params -> "Filter ${params.resourceName} by a selected field." },
        listOf(
            CrudResponseSpecification(HttpStatus.OK, "The %s matching the filter criteria as a JSON object."),
            CrudResponseSpecification(
                HttpStatus.NOT_FOUND,
                "No %s matching the filter criteria could be found.",
                isErrorResponse = true
            )
        )
    ),
    IMPORT(
        { params ->
            val externalName =
                params.externalResourceName
                    ?: throw IllegalArgumentException("External resource name not set.")
            "Import ${params.resourceName} from $externalName."
        },
        listOf(
            CrudResponseSpecification(HttpStatus.CREATED, "The imported %s as a JSON object."),
            CrudResponseSpecification(
                HttpStatus.BAD_REQUEST,
                "Validation failed or the external %s is invalid.",
                isErrorResponse = true,
                isExternalResource = true
            ),
            CrudResponseSpecification(
                HttpStatus.NOT_FOUND,
                "The external %s could not be found.",
                isErrorResponse = true,
                isExternalResource = true
            )
        )
    )
}
