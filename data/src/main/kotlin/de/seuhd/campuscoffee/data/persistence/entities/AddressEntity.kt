package de.seuhd.campuscoffee.data.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * Embedded database entity for an address.
 */
@Embeddable
class AddressEntity {
    var street: String? = null

    @field:Column(name = "house_number")
    var houseNumber: Int? = null

    @field:Column(name = "house_number_suffix")
    var houseNumberSuffix: Char? = null

    @field:Column(name = "postal_code")
    var postalCode: Int? = null

    var city: String? = null
}
