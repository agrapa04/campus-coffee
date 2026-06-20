package de.seuhd.campuscoffee.api.openapi

/**
 * Available resources with their singular and plural forms, resolving the right form per operation.
 *
 * @property singular the singular display name of the resource
 * @property plural the plural display name of the resource
 */
enum class Resource(
    val singular: String,
    val plural: String
) {
    NONE("", ""),
    USER("user", "users"),
    POS("POS", "POS"),
    REVIEW("review", "reviews"),
    OSM_NODE("OpenStreetMap node", "OpenStreetMap nodes");

    /**
     * Returns the appropriate form for the operation: GET_ALL uses the plural, all others the singular.
     *
     * @param operation the operation whose display form to resolve
     */
    fun displayNameForOperation(operation: Operation): String = if (operation == Operation.GET_ALL) plural else singular
}
