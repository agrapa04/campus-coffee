package de.seuhd.campuscoffee.data.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tests [IdGeneratorConfiguration] and the [SeededUuidGenerator] it builds: a numeric
 * `campus-coffee.id.seed` produces the same id sequence every time, and `random` (or a blank value)
 * produces a different id on each call.
 */
class IdGeneratorConfigurationTest {
    private val configuration = IdGeneratorConfiguration()

    @Test
    fun `a blank or random seed produces a different id on each call`() {
        for (seed in listOf("random", "RANDOM", " ")) {
            val idGenerator = configuration.entityIdGenerator(seed)
            assertThat(idGenerator.newId()).isNotEqualTo(idGenerator.newId())
        }
    }

    @Test
    fun `the same numeric seed produces the same id sequence on two generators`() {
        val first = configuration.entityIdGenerator("42")
        val second = configuration.entityIdGenerator("42")

        repeat(5) { assertThat(first.newId()).isEqualTo(second.newId()) }
    }

    @Test
    fun `two different seeds produce different first ids`() {
        assertThat(SeededUuidGenerator(1L).newId()).isNotEqualTo(SeededUuidGenerator(2L).newId())
    }

    @Test
    fun `the default entity and event seeds produce different id sequences`() {
        // the entity and event generators use separate seeds (42 and 100 by default), so an event id never
        // coincides with an entity id
        val entityIds = configuration.entityIdGenerator("42").let { gen -> List(3) { gen.newId() } }
        val eventIds = configuration.eventIdGenerator("100").let { gen -> List(3) { gen.newId() } }

        assertThat(eventIds).doesNotContainAnyElementsOf(entityIds)
    }

    @Test
    fun `seed 42 produces the ids documented in the README and the instructor demo`() {
        // these back the concrete ids the docs reference for the fixture data, which is loaded in a fixed
        // order (users, then POS, then reviews); a change here means the documented ids must be updated
        val idGenerator = SeededUuidGenerator(42L)
        val firstIds = List(6) { idGenerator.newId() }

        assertThat(firstIds[0]).isEqualTo(JANE_DOE_ID)
        assertThat(firstIds[2]).isEqualTo(STUDENT2023_ID)
        assertThat(firstIds[5]).isEqualTo(SCHMELZPUNKT_ID)
    }

    private companion object {
        // the ids SeededUuidGenerator(42) assigns to these fixtures, also shown in the README and the
        // instructor demo
        val JANE_DOE_ID: UUID = UUID.fromString("ba419d35-0dfe-8af7-aee7-bbe10c45c028")
        val STUDENT2023_ID: UUID = UUID.fromString("aa616abe-1761-0c9a-e743-67bd738597dc")
        val SCHMELZPUNKT_ID: UUID = UUID.fromString("eb5910f1-26e6-bc6f-6fbd-df557096b883")
    }

    @Test
    fun `reset restarts a seeded generator's sequence and leaves a random one random`() {
        val seeded = configuration.entityIdGenerator("42")
        val firstTwo = listOf(seeded.newId(), seeded.newId())
        seeded.reset()
        assertThat(listOf(seeded.newId(), seeded.newId())).isEqualTo(firstTwo)

        val random = configuration.entityIdGenerator("random")
        random.reset()
        assertThat(random.newId()).isNotEqualTo(random.newId())
    }
}
