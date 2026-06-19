package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.data.implementations.PosDataServiceImpl
import de.seuhd.campuscoffee.data.implementations.ReviewApprovalDataServiceImpl
import de.seuhd.campuscoffee.data.implementations.ReviewDataServiceImpl
import de.seuhd.campuscoffee.data.implementations.UserDataServiceImpl
import de.seuhd.campuscoffee.data.integration.AbstractDataIntegrationTest
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Verifies that in the default (relational) mode the data-service ports resolve to the plain relational
 * implementations. The event-sourced decorators are `@ConditionalOnProperty` on the event-sourcing mode, so
 * in this default-mode context they are not created and the relational implementations are injected.
 */
class RelationalModeWiringTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posDataService: PosDataService

    @Autowired
    private lateinit var userDataService: UserDataService

    @Autowired
    private lateinit var reviewDataService: ReviewDataService

    @Autowired
    private lateinit var reviewApprovalDataService: ReviewApprovalDataService

    @Test
    fun `default mode wires the relational data adapters`() {
        assertThat(posDataService).isInstanceOf(PosDataServiceImpl::class.java)
        assertThat(userDataService).isInstanceOf(UserDataServiceImpl::class.java)
        assertThat(reviewDataService).isInstanceOf(ReviewDataServiceImpl::class.java)
        assertThat(reviewApprovalDataService).isInstanceOf(ReviewApprovalDataServiceImpl::class.java)
    }
}
