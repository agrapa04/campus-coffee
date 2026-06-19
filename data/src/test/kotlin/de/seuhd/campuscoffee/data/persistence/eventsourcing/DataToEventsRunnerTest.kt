package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Tests the data-to-events import runner. Both the `data-to-events-on-startup` and
 * `events-to-data-on-startup` flags are set, so both runner beans exist; the test then drives the import
 * runner directly over a prepared pre-event-sourcing state (the read tables hold rows but the event log is
 * empty) and asserts the two runners are ordered import-before-rebuild.
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
    fun `imports existing rows as INSERT events and skips a type whose log already has events`() {
        seedRowsToImport()

        // drive it through the StartupTask entry point the initializer uses
        dataToEventsRunner.run()

        // one INSERT event per existing row (a user, a POS, a review)
        assertThat(eventRepository.findAll()).allMatch { it.changeType == ChangeType.INSERT }
        val imported = eventRepository.count()
        assertThat(imported).isEqualTo(3L)

        // a second run is idempotent: every type's log is non-empty, so nothing is appended again
        dataToEventsRunner.importRowsAsEvents()
        assertThat(eventRepository.count()).isEqualTo(imported)
    }

    @Test
    fun `both startup runners are wired and the import runner is ordered before the rebuild runner`() {
        assertThat(dataToEventsRunner).isNotNull
        assertThat(eventsToDataRunner).isNotNull
        assertThat(dataToEventsRunner.order).isLessThan(eventsToDataRunner.order)
    }

    /** Creates rows through the decorators, then drops the events to mimic a database that predates event sourcing. */
    private fun seedRowsToImport() {
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        val author =
            userDataService.upsert(
                TestFixtures.getUserFixturesForInsertion().first().copy(
                    passwordHash = "{bcrypt}\$2a\$10\$importhashvalue00000"
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
