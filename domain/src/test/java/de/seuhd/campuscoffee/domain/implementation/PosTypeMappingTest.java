package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException;
import de.seuhd.campuscoffee.domain.model.enums.CampusType;
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity;
import de.seuhd.campuscoffee.domain.model.enums.PosType;
import de.seuhd.campuscoffee.domain.model.objects.OsmNode;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests how {@link PosServiceImpl#importFromOsmNode} resolves the resulting {@link PosType} from an
 * OSM amenity, and how it reports an unparsable postcode.
 */
@ExtendWith(MockitoExtension.class)
class PosTypeMappingTest {

    private static final long NODE_ID = 42L;

    @Mock
    private PosDataService posDataService;

    @Mock
    private OsmDataService osmDataService;

    private PosServiceImpl posService;

    @BeforeEach
    void setUp() {
        posService = new PosServiceImpl(posDataService, osmDataService);
    }

    static Stream<Arguments> amenityToPosType() {
        return Stream.of(
                arguments(OsmAmenity.CAFE, PosType.CAFE),
                arguments(OsmAmenity.ICE_CREAM, PosType.CAFE),
                arguments(OsmAmenity.VENDING_MACHINE, PosType.VENDING_MACHINE),
                arguments(OsmAmenity.FOOD_COURT, PosType.CAFETERIA),
                arguments(OsmAmenity.BAR, PosType.OTHER),
                arguments(OsmAmenity.BIERGARTEN, PosType.OTHER),
                arguments(OsmAmenity.PUB, PosType.OTHER),
                arguments(OsmAmenity.RESTAURANT, PosType.OTHER),
                arguments(OsmAmenity.FAST_FOOD, PosType.OTHER)
        );
    }

    @ParameterizedTest
    @MethodSource("amenityToPosType")
    void mapsAmenityToPosType(OsmAmenity amenity, PosType expectedType) {
        when(osmDataService.fetchNode(NODE_ID)).thenReturn(nodeWith(amenity, "69117"));
        when(posDataService.upsert(any(Pos.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pos imported = posService.importFromOsmNode(NODE_ID, CampusType.INF);

        assertThat(imported.type()).isEqualTo(expectedType);
    }

    @Test
    void importMapsAllOsmNodeFieldsToPos() {
        OsmNode node = nodeWith(OsmAmenity.CAFE, "69117");
        when(osmDataService.fetchNode(NODE_ID)).thenReturn(node);
        when(posDataService.upsert(any(Pos.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pos imported = posService.importFromOsmNode(NODE_ID, CampusType.INF);

        // every OSM field is carried into the corresponding POS field, and the requested campus is set
        assertThat(imported.name()).isEqualTo(node.name());
        assertThat(imported.description()).isEqualTo(node.description());
        assertThat(imported.street()).isEqualTo(node.street());
        assertThat(imported.houseNumber()).isEqualTo(node.houseNumber());
        assertThat(imported.city()).isEqualTo(node.city());
        assertThat(imported.postalCode()).isEqualTo(Integer.parseInt(node.postcode()));
        assertThat(imported.campus()).isEqualTo(CampusType.INF);
    }

    @Test
    void unparsablePostcodeIsReportedAsMissingField() {
        when(osmDataService.fetchNode(NODE_ID)).thenReturn(nodeWith(OsmAmenity.CAFE, "not-a-number"));

        assertThatThrownBy(() -> posService.importFromOsmNode(NODE_ID, CampusType.INF))
                .isInstanceOf(MissingFieldException.class);
    }

    private OsmNode nodeWith(OsmAmenity amenity, String postcode) {
        return OsmNode.builder()
                .nodeId(NODE_ID)
                .name("Campus Cafe")
                .amenity(amenity)
                .city("Heidelberg")
                .street("Hauptstrasse")
                .houseNumber("5")
                .postcode(postcode)
                .description("An arbitrary description.")
                .build();
    }
}
