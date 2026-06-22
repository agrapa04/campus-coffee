package de.seuhd.campuscoffee.data.persistence.eventsourcing
import de.seuhd.campuscoffee.data.configuration.PersistenceMode
import de.seuhd.campuscoffee.data.configuration.PersistenceProperties
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewApprovalRepository
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository
import de.seuhd.campuscoffee.data.persistence.repositories.UserRepository
import de.seuhd.campuscoffee.domain.ports.StartupTask
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Rebuilds the relational read tables from the event log on startup, when
 * `campus-coffee.persistence.events-to-data-on-startup` is true: clears the tables and replays every event
 * in append order through the [ReadModelProjector]. It runs only in event sourcing mode (where the log is
 * the source of truth); in relational mode it logs and skips, since the tables are authoritative there and
 * replaying would delete their contents. It also skips when the log is empty, so it cannot clear a
 * populated read model with nothing to replay back into it.
 *
 * The application's startup initializer invokes the runners in their `ORDER` sequence (before the web server
 * accepts requests), so the import-before-rebuild order holds and a rebuild sees the events that the import
 * may have just added.
 *
 * The replay writes the ids and the `createdAt`/`updatedAt` from the event bodies. The reviews'
 * optimistic locking version column restarts from zero, which has no effect because nothing compares a
 * version across a rebuild.
 */
@Component
@ConditionalOnProperty(name = ["campus-coffee.persistence.events-to-data-on-startup"], havingValue = "true")
class EventsToDataRunner(
    private val properties: PersistenceProperties,
    private val eventRepository: EventRepository,
    private val projector: ReadModelProjector,
    private val posRepository: PosRepository,
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository,
    private val reviewApprovalRepository: ReviewApprovalRepository
) : StartupTask {
    override val order = ORDER

    @Transactional
    override fun run() = rebuildFromLog()

    /**
     * Clears the read tables and replays every event in append order to rebuild the read model. Skips in
     * relational mode (the tables are authoritative there) and when the log is empty (so it never wipes a
     * populated read model with nothing to replay back).
     */
    @Transactional
    fun rebuildFromLog() {
        if (properties.mode != PersistenceMode.EVENT_SOURCING) {
            log.info {
                "Skipping the events-to-data rebuild: persistence mode is ${properties.mode}, not event-sourcing."
            }
            return
        }
        if (eventRepository.count() == 0L) {
            // an empty log against possibly-populated tables: rebuilding would only wipe them, so refuse
            log.warn { "Skipping the events-to-data rebuild: the event log is empty; not clearing the read tables." }
            return
        }
        clearReadTables()
        val events = eventRepository.findAllByOrderBySeqAsc()
        events.forEach { projector.apply(it) }
        log.info { "Rebuilt the read model from ${events.size} events in the log." }
    }

    /** Empties the read tables in foreign key order (approvals and reviews, then POS and users). */
    private fun clearReadTables() {
        // approvals and reviews carry the foreign keys, so clear them before the POS and users they reference
        reviewApprovalRepository.deleteAllInBatch()
        reviewRepository.deleteAllInBatch()
        posRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    companion object {
        /** Runs after [DataToEventsRunner], so a rebuild sees the events that the import may have just added. */
        const val ORDER = 100
        private val log = KotlinLogging.logger {}
    }
}
