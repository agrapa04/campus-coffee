package de.seuhd.campuscoffee.data.persistence.eventsourcing
import de.seuhd.campuscoffee.data.configuration.PersistenceProperties
import de.seuhd.campuscoffee.data.implementations.ReviewApprovalDataServiceImpl
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Event-sourcing review-approval data adapter, active only when `campus-coffee.persistence.mode` is
 * `event-sourcing`. A Decorator around the relational [ReviewApprovalDataServiceImpl] (both are adapters
 * for the same `ReviewApprovalDataService` port): the `delegate` is typed against the port and pinned to the
 * relational bean with `@Qualifier(ReviewApprovalDataServiceImpl.BEAN_NAME)`, so the wrapper shares only the
 * interface with the wrappee. `countByReviewId` delegates to it, while recording an
 * approval becomes an event-first insert. The read model's unique (review, user) constraint still rejects a
 * repeat approval as a [DuplicationException][de.seuhd.campuscoffee.domain.exceptions.DuplicationException].
 */
@Service
@Primary
@ConditionalOnProperty(
    name = [PersistenceProperties.MODE_PROPERTY],
    havingValue = PersistenceProperties.EVENT_SOURCING_MODE,
    // a missing mode key activates the decorator, matching PersistenceProperties' EVENT_SOURCING default
    matchIfMissing = true
)
class EventSourcedReviewApprovalDataService(
    @param:Qualifier(ReviewApprovalDataServiceImpl.BEAN_NAME) private val delegate: ReviewApprovalDataService,
    private val writer: EventSourcedWriter
) : ReviewApprovalDataService by delegate {
    @Transactional
    override fun record(approval: ReviewApproval): ReviewApproval =
        writer.create { id, now -> approval.copy(id = id, createdAt = now, updatedAt = now) }

    @Transactional
    override fun clear() = writer.clear(ReviewApproval::class, delegate::clear)
}
