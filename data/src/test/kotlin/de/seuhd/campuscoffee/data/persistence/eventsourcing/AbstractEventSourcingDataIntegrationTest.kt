package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.data.integration.AbstractDataIntegrationTest
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Base class for the event-sourcing data layer integration tests. Reuses [AbstractDataIntegrationTest]'s
 * PostgreSQL container and read-model repositories, and adds `campus-coffee.persistence.mode=event-sourcing`
 * so the context runs in event-sourcing mode. With that property set, the `@Primary` event-sourced
 * decorators win injection, so the autowired data-service ports below are those decorators. It also clears
 * the event log before each test.
 */
@TestPropertySource(properties = ["campus-coffee.persistence.mode=event-sourcing"])
abstract class AbstractEventSourcingDataIntegrationTest : AbstractDataIntegrationTest() {
    @Autowired
    protected lateinit var posDataService: PosDataService

    @Autowired
    protected lateinit var userDataService: UserDataService

    @Autowired
    protected lateinit var reviewDataService: ReviewDataService

    @Autowired
    protected lateinit var reviewApprovalDataService: ReviewApprovalDataService

    @Autowired
    protected lateinit var eventRepository: EventRepository

    @BeforeEach
    fun clearEventLog() {
        eventRepository.deleteAllInBatch()
    }
}
