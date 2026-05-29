package de.seuhd.campuscoffee.domain.model.enums

/**
 * OpenStreetMap amenity types relevant for CampusCoffee POS.
 * Based on [wiki.openstreetmap.org/wiki/Key:amenity](https://wiki.openstreetmap.org/wiki/Key:amenity).
 */
enum class OsmAmenity {
    BAR,
    BIERGARTEN,
    CAFE,
    FAST_FOOD,
    FOOD_COURT,
    ICE_CREAM,
    PUB,
    RESTAURANT,
    VENDING_MACHINE;

    companion object {
        /**
         * Parses an OpenStreetMap amenity string value to its corresponding enum constant.
         *
         * @param osmValue the OSM string value (e.g., "bar", "fast_food")
         * @return the matching enum constant, or null if no match is found
         */
        @JvmStatic
        fun fromOsmValue(osmValue: String): OsmAmenity? = entries.firstOrNull { it.name.lowercase() == osmValue }
    }
}
