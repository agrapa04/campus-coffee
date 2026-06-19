package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Tests the events-to-data rebuild runner in event-sourcing mode: it clears the read tables and replays
 * the whole log, reconstructing the rows that were there before.
 */
@TestPropertySource(properties = ["campus-coffee.persistence.events-to-data-on-startup=true"])
class EventsToDataRunnerTest : AbstractEventSourcingDataIntegrationTest() {
    @Autowired
    private lateinit var runner: EventsToDataRunner

    @Test
    fun `rebuilds the read tables from the log`() {
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        val author =
            userDataService.upsert(
                TestFixtures.getUserFixturesForInsertion().first().copy(
                    passwordHash = "{bcrypt}\$2a\$10\$rebuildhashvalue00000"
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
        val posBefore = posDataService.getAll().sortedBy { it.name }
        val usersBefore = userDataService.getAll().sortedBy { it.loginName }
        val reviewsBefore = reviewDataService.getAll().sortedBy { it.review }

        // drive it through the StartupTask entry point the initializer uses
        runner.run()

        // the tables were cleared and rebuilt from the log, so the rows reappear unchanged (compared by
        // content, not just count, so a rebuild that corrupted a field would be caught)
        assertThat(posDataService.getAll().sortedBy { it.name }).usingRecursiveComparison().isEqualTo(posBefore)
        assertThat(userDataService.getAll().sortedBy { it.loginName }).usingRecursiveComparison().isEqualTo(usersBefore)
        assertThat(
            reviewDataService.getAll().sortedBy { it.review }
        ).usingRecursiveComparison().isEqualTo(reviewsBefore)
    }

    @Test
    fun `rebuild skips an empty log and leaves the read tables intact`() {
        // rows present, log emptied: a rebuild from an empty log would wipe the tables, so the runner must
        // refuse (this is the data-loss state the misordered both-flags startup could otherwise reach)
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        eventRepository.deleteAllInBatch()

        runner.rebuildFromLog()

        assertThat(posDataService.getAll()).hasSize(1)
        assertThat(posDataService.getById(requireNotNull(pos.id)).name).isEqualTo(pos.name)
    }
}
