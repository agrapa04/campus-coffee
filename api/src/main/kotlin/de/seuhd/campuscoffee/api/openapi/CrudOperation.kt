package de.seuhd.campuscoffee.api.openapi

/**
 * Unified annotation for CRUD operations on a controller method. Drives the generated OpenAPI
 * summary and responses; see [CrudOperationCustomizer].
 *
 * @property operation         the type of CRUD operation
 * @property resource          the resource being operated on (handles singular/plural automatically)
 * @property externalResource  optional external resource for IMPORT operations
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CrudOperation(
    val operation: Operation,
    val resource: Resource,
    val externalResource: Resource = Resource.NONE
)
