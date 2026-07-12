package de.seuhd.campuscoffee.domain.ports.api

import de.seuhd.campuscoffee.domain.exceptions.ForbiddenException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import java.util.UUID

enum class EntityType {
    POS, USER, REVIEW
}

interface RevertPort{
    /**
     * Reverts the entity of the given type and ID to its previous state, if the observed version matches the current version.
     * @param entityType The type of the entity to revert.
     * @param entityId The ID of the entity to revert.
     * @param observedVersion The version of the entity that the caller has observed.
     * @throws NotFoundException if the entity does not exist.
     * @throws ForbiddenException if the observed version does not match the current version.
     */
    @Throws(NotFoundException::class, ForbiddenException::class)
    fun revertEntity(entityType: EntityType, entityId: UUID, observedVersion: Long)
}
