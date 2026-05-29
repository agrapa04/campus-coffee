package de.seuhd.campuscoffee.domain.exceptions

import de.seuhd.campuscoffee.domain.model.objects.DomainModel

/**
 * Thrown when an update is rejected because the entity was modified concurrently since it was read
 * (an optimistic-locking conflict). The caller should reload the current state and retry.
 *
 * @param domainClass class of the domain object (e.g., "Review")
 * @param id          the ID of the entity that was modified concurrently
 */
class ConcurrentUpdateException(
    domainClass: Class<out DomainModel<*>>,
    id: Any?
) : RuntimeException(
        "${domainClass.simpleName} with ID $id was modified concurrently. Please reload it and retry."
    )
