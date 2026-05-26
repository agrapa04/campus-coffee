package de.seuhd.campuscoffee.domain.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link OsmAmenity#fromOsmValue(String)}, which resolves an OpenStreetMap amenity string to its
 * enum constant. The string parsing lives in the domain module, so it is pinned by a domain-local test
 * rather than only through the data-layer OSM tests.
 */
class OsmAmenityTest {

    /**
     * Every enum constant resolves from its lowercase name, and the resolved value is that exact constant.
     */
    @ParameterizedTest
    @EnumSource(OsmAmenity.class)
    void resolvesEveryConstantFromItsLowercaseName(OsmAmenity amenity) {
        Optional<OsmAmenity> resolved = OsmAmenity.fromOsmValue(amenity.name().toLowerCase());

        assertThat(resolved).contains(amenity);
    }

    /**
     * Resolution is case-sensitive on the lowercase form: the uppercase enum name does not match.
     */
    @ParameterizedTest
    @ValueSource(strings = {"CAFE", "Cafe", "FAST_FOOD"})
    void doesNotResolveUppercaseOrMixedCase(String osmValue) {
        assertThat(OsmAmenity.fromOsmValue(osmValue)).isEmpty();
    }

    /**
     * An unknown amenity string resolves to an empty Optional rather than any constant.
     */
    @ParameterizedTest
    @ValueSource(strings = {"hospital", "parking", "", "cafe "})
    void returnsEmptyForUnknownValues(String osmValue) {
        assertThat(OsmAmenity.fromOsmValue(osmValue)).isEmpty();
    }
}
