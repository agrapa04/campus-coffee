package de.seuhd.campuscoffee.data.persistence.entities

import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * Database entity for a point-of-sale (POS).
 */
@jakarta.persistence.Entity
@Table(name = "pos")
class PosEntity : Entity() {
    @field:Column(name = NAME_COLUMN)
    var name: String? = null

    var description: String? = null

    @field:Enumerated(EnumType.STRING)
    var type: PosType? = null

    @field:Enumerated(EnumType.STRING)
    var campus: CampusType? = null

    @field:Embedded
    var address: AddressEntity? = null

    // Optimistic locking version; the losing side of a concurrent update returns 409 instead of silently
    // overwriting. Defaults to 0 (not null) across these entities so a detached copy built by the mapper is
    // read as detached, not transient: a null version reads as transient and breaks a @ManyToOne to it, such
    // as a review's reference to its POS and author.
    @field:Version
    @field:Column(name = "version")
    var version: Long? = 0

    companion object {
        const val NAME_COLUMN = "name"

        /** Name of the unique constraint on `name`, declared in the Flyway migration. */
        const val NAME_UNIQUE_CONSTRAINT = "uq_pos_name"
    }
}
