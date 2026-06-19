package de.seuhd.campuscoffee.data.persistence.eventsourcing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies that, with `campus-coffee.persistence.mode=event-sourcing`, the data-service ports resolve to
 * the event-sourced decorators (the `@Primary` beans), not the relational implementations.
 */
class EventSourcingModeWiringTest : AbstractEventSourcingDataIntegrationTest() {
    @Test
    fun `event-sourcing mode wires the event-sourced decorators`() {
        assertThat(posDataService).isInstanceOf(EventSourcedPosDataService::class.java)
        assertThat(userDataService).isInstanceOf(EventSourcedUserDataService::class.java)
        assertThat(reviewDataService).isInstanceOf(EventSourcedReviewDataService::class.java)
        assertThat(reviewApprovalDataService).isInstanceOf(EventSourcedReviewApprovalDataService::class.java)
    }
}
