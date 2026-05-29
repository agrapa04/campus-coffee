package de.seuhd.campuscoffee.domain.model.enums

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests [OsmAmenity.fromOsmValue], which resolves an OpenStreetMap amenity string to its enum
 * constant. The parsing lives in the domain module, so it is pinned by a domain-local test rather
 * than only through the data-layer OSM tests.
 */
class OsmAmenityTest {
    @ParameterizedTest
    @EnumSource(OsmAmenity::class)
    fun `fromOsmValue resolves every constant from its lowercase name`(amenity: OsmAmenity) {
        assertThat(OsmAmenity.fromOsmValue(amenity.name.lowercase())).isEqualTo(amenity)
    }

    @ParameterizedTest
    @ValueSource(strings = ["CAFE", "Cafe", "FAST_FOOD"])
    fun `fromOsmValue returns null for uppercase or mixed case`(osmValue: String) {
        assertThat(OsmAmenity.fromOsmValue(osmValue)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["hospital", "parking", "", "cafe "])
    fun `fromOsmValue returns null for unknown values`(osmValue: String) {
        assertThat(OsmAmenity.fromOsmValue(osmValue)).isNull()
    }
}
