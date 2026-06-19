package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.data.integration.AbstractDataIntegrationTest
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Tests the data-to-events adoption runner in the default (relational) mode. The
 * `data-to-events-on-startup` flag is set, so the runner bean exists, but in relational mode
 * `seedLogFromRows` logs and returns without appending events: adoption only makes sense when switching to
 * event-sourcing, since adopting in relational mode would write a snapshot the live writes then diverge
 * from.
 */
@TestPropertySource(properties = ["campus-coffee.persistence.data-to-events-on-startup=true"])
class DataToEventsRunnerRelationalSkipTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var runner: DataToEventsRunner

    @Autowired
    private lateinit var posDataService: PosDataService

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Test
    fun `skips adoption in relational mode and appends no events`() {
        posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        eventRepository.deleteAllInBatch()

        runner.seedLogFromRows()

        assertThat(eventRepository.count()).isZero()
    }
}
