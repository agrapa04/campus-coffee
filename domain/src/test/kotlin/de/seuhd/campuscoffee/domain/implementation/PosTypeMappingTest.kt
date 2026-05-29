package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.OsmAmenity
import de.seuhd.campuscoffee.domain.model.enums.PosType
import de.seuhd.campuscoffee.domain.model.objects.OsmNode
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.stream.Stream

/**
 * Tests how [PosServiceImpl.importFromOsmNode] resolves the resulting [PosType] from an OSM amenity,
 * and how it reports an unparsable postcode.
 */
@ExtendWith(MockitoExtension::class)
class PosTypeMappingTest {
    @Mock
    private lateinit var posDataService: PosDataService

    @Mock
    private lateinit var osmDataService: OsmDataService

    private lateinit var posService: PosServiceImpl

    @BeforeEach
    fun setUp() {
        posService = PosServiceImpl(posDataService, osmDataService)
    }

    @ParameterizedTest
    @MethodSource("amenityToPosType")
    fun mapsAmenityToPosType(
        amenity: OsmAmenity,
        expectedType: PosType
    ) {
        whenever(osmDataService.fetchNode(NODE_ID)).thenReturn(nodeWith(amenity, "69117"))
        whenever(posDataService.upsert(any<Pos>())).thenAnswer { it.getArgument<Pos>(0) }

        val imported = posService.importFromOsmNode(NODE_ID, CampusType.INF)

        assertThat(imported.type).isEqualTo(expectedType)
    }

    @Test
    fun importMapsAllOsmNodeFieldsToPos() {
        val node = nodeWith(OsmAmenity.CAFE, "69117")
        whenever(osmDataService.fetchNode(NODE_ID)).thenReturn(node)
        whenever(posDataService.upsert(any<Pos>())).thenAnswer { it.getArgument<Pos>(0) }

        val imported = posService.importFromOsmNode(NODE_ID, CampusType.INF)

        // every OSM field is carried into the corresponding POS field, and the requested campus is set
        assertThat(imported.name).isEqualTo(node.name)
        assertThat(imported.description).isEqualTo(node.description)
        assertThat(imported.street).isEqualTo(node.street)
        assertThat(imported.houseNumber).isEqualTo(node.houseNumber)
        assertThat(imported.city).isEqualTo(node.city)
        assertThat(imported.postalCode).isEqualTo(node.postcode.toInt())
        assertThat(imported.campus).isEqualTo(CampusType.INF)
    }

    @Test
    fun unparsablePostcodeIsReportedAsMissingField() {
        whenever(osmDataService.fetchNode(NODE_ID)).thenReturn(nodeWith(OsmAmenity.CAFE, "not-a-number"))

        assertThatThrownBy { posService.importFromOsmNode(NODE_ID, CampusType.INF) }
            .isInstanceOf(MissingFieldException::class.java)
    }

    private fun nodeWith(
        amenity: OsmAmenity,
        postcode: String
    ): OsmNode =
        OsmNode(
            nodeId = NODE_ID,
            name = "Campus Cafe",
            amenity = amenity,
            city = "Heidelberg",
            street = "Hauptstrasse",
            houseNumber = "5",
            postcode = postcode,
            description = "An arbitrary description."
        )

    companion object {
        private const val NODE_ID = 42L

        @JvmStatic
        fun amenityToPosType(): Stream<Arguments> =
            Stream.of(
                arguments(OsmAmenity.CAFE, PosType.CAFE),
                arguments(OsmAmenity.ICE_CREAM, PosType.CAFE),
                arguments(OsmAmenity.VENDING_MACHINE, PosType.VENDING_MACHINE),
                arguments(OsmAmenity.FOOD_COURT, PosType.CAFETERIA),
                arguments(OsmAmenity.BAR, PosType.OTHER),
                arguments(OsmAmenity.BIERGARTEN, PosType.OTHER),
                arguments(OsmAmenity.PUB, PosType.OTHER),
                arguments(OsmAmenity.RESTAURANT, PosType.OTHER),
                arguments(OsmAmenity.FAST_FOOD, PosType.OTHER)
            )
    }
}
