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
    val description: String,
) : DomainModel<Long> {

    override val id: Long get() = nodeId

    // --- temporary bridges so the still-Java tests keep compiling; removed once the tests are Kotlin ---
    fun nodeId() = nodeId
    fun city() = city
    fun houseNumber() = houseNumber
    fun postcode() = postcode
    fun street() = street
    fun amenity() = amenity
    fun name() = name
    fun description() = description

    class Builder {
        private var nodeId: Long? = null
        private var city: String? = null
        private var houseNumber: String? = null
        private var postcode: String? = null
        private var street: String? = null
        private var amenity: OsmAmenity? = null
        private var name: String? = null
        private var description: String? = null

        fun nodeId(v: Long) = apply { nodeId = v }
        fun city(v: String) = apply { city = v }
        fun houseNumber(v: String) = apply { houseNumber = v }
        fun postcode(v: String) = apply { postcode = v }
        fun street(v: String) = apply { street = v }
        fun amenity(v: OsmAmenity) = apply { amenity = v }
        fun name(v: String) = apply { name = v }
        fun description(v: String) = apply { description = v }

        fun build() = OsmNode(
            nodeId!!, city!!, houseNumber!!, postcode!!, street!!, amenity!!, name!!, description!!,
        )
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
