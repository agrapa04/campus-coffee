package de.seuhd.campuscoffee.domain.exceptions

import de.seuhd.campuscoffee.domain.model.objects.DomainModel

/**
 * Generic exception thrown when an entity is not found in the database.
 * Supports finding by ID or by a specific field name and value.
 */
class NotFoundException : RuntimeException {
    /**
     * Creates an exception for an entity not found by ID.
     *
     * @param domainClass class of domain object (e.g., "Pos", "User")
     * @param id          the ID that was not found
     */
    constructor(domainClass: Class<out DomainModel<*>>, id: Any?) :
        super("${domainClass.simpleName} with ID $id does not exist.")

    /**
     * Creates an exception for an entity not found by a specific field.
     *
     * @param domainClass class of domain object (e.g., "Pos", "User")
     * @param fieldName   the field name (e.g., "name", "login name")
     * @param fieldValue  the field value that was not found
     */
    constructor(domainClass: Class<out DomainModel<*>>, fieldName: String, fieldValue: String) :
        super("${domainClass.simpleName} with $fieldName '$fieldValue' does not exist.")
}
