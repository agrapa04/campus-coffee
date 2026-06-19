package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Tests the data-to-events adoption runner. Both the `data-to-events-on-startup` and
 * `events-to-data-on-startup` flags are set, so both runner beans exist; the test then drives the adoption
 * runner directly over a prepared "adopted database" state (the read tables hold rows but the event log is
 * empty) and asserts the two runners are ordered adopt-before-rebuild.
 */
@TestPropertySource(
    properties = [
        "campus-coffee.persistence.data-to-events-on-startup=true",
        "campus-coffee.persistence.events-to-data-on-startup=true"
    ]
)
class DataToEventsRunnerTest : AbstractEventSourcingDataIntegrationTest() {
    @Autowired
    private lateinit var dataToEventsRunner: DataToEventsRunner

    @Autowired
    private lateinit var eventsToDataRunner: EventsToDataRunner

    @Test
    fun `adopts existing rows as INSERT events and skips a type whose log already has events`() {
        seedAdoptedDatabaseRows()

        dataToEventsRunner.seedLogFromRows()

        // one INSERT event per existing row (a user, a POS, a review)
        assertThat(eventRepository.findAll()).allMatch { it.changeType == ChangeType.INSERT }
        val seeded = eventRepository.count()
        assertThat(seeded).isEqualTo(3L)

        // a second run is idempotent: every type's log is non-empty, so nothing is appended again
        dataToEventsRunner.seedLogFromRows()
        assertThat(eventRepository.count()).isEqualTo(seeded)
    }

    @Test
    fun `both startup runners are wired and the adopt runner is ordered before the rebuild runner`() {
        assertThat(dataToEventsRunner).isNotNull
        assertThat(eventsToDataRunner).isNotNull
        assertThat(DataToEventsRunner.ORDER).isLessThan(EventsToDataRunner.ORDER)
    }

    /** Creates rows through the decorators, then drops the events to mimic a database that predates event sourcing. */
    private fun seedAdoptedDatabaseRows() {
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        val author =
            userDataService.upsert(
                TestFixtures.getUserFixturesForInsertion().first().copy(
                    passwordHash = "{bcrypt}\$2a\$10\$adopthashvalue000000"
                )
            )
        reviewDataService.upsert(
            Review(
                pos = pos,
                author = author,
                review = "A review long enough to pass.",
                approvalCount = 0,
                approved = false
            )
        )
        eventRepository.deleteAllInBatch()
    }
}
