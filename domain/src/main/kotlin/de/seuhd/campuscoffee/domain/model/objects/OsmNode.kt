package de.seuhd.campuscoffee.domain.model.objects

import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity

/**
 * An OpenStreetMap node with the Point of Sale information relevant to CampusCoffee. This is the
 * domain model for OSM data before it is converted to a [Pos].
 */
data class OsmNode(
    val nodeId: Long,
    val city: String,
    val houseNumber: String,
    val postcode: String,
    val street: String,
    val amenity: OsmAmenity,
    val name: String,
    val description: String
) : DomainModel<Long> {
    override val id: Long get() = nodeId
}
