package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.data.integration.AbstractDataIntegrationTest
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Tests the events-to-data rebuild runner in the default (relational) mode. The
 * `events-to-data-on-startup` flag is set, so the runner bean exists, but in relational mode `rebuildFromLog`
 * logs and returns without touching the tables, because the relational tables are authoritative there and a
 * replay would delete their contents.
 */
@TestPropertySource(properties = ["campus-coffee.persistence.events-to-data-on-startup=true"])
class EventsToDataRunnerRelationalSkipTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var runner: EventsToDataRunner

    @Autowired
    private lateinit var posDataService: PosDataService

    @Test
    fun `does not touch the tables in relational mode`() {
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())

        runner.rebuildFromLog()

        // in relational mode the runner returns early, so the row is untouched (a rebuild would delete it)
        assertThat(posRepository.findById(requireNotNull(pos.id))).isPresent
    }
}
