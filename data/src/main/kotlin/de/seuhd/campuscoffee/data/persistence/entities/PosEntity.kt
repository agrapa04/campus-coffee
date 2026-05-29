package de.seuhd.campuscoffee.data.persistence.entities

import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

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

    companion object {
        const val NAME_COLUMN = "name"

        /** Name of the unique constraint on `name`, declared in the Flyway migration. */
        const val NAME_UNIQUE_CONSTRAINT = "uq_pos_name"
    }
}
