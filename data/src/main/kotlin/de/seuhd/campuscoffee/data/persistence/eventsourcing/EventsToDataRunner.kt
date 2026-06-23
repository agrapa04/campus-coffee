package de.seuhd.campuscoffee.data.persistence.eventsourcing
import de.seuhd.campuscoffee.data.configuration.PersistenceMode
import de.seuhd.campuscoffee.data.configuration.PersistenceProperties
import de.seuhd.campuscoffee.data.persistence.entities.Entity
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewApprovalRepository
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository
import de.seuhd.campuscoffee.data.persistence.repositories.UserRepository
import de.seuhd.campuscoffee.domain.model.objects.DomainModel
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.StartupTask
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Rebuilds the relational read tables from the event log on startup, when
 * `campus-coffee.persistence.events-to-data-on-startup` is true: clears the tables and replays every event
 * in append order through the [ReadModelProjector]. It runs only in event sourcing mode (where the log is
 * the source of truth); in relational mode it logs and skips, since the tables are authoritative there and
 * replaying would delete their contents. It also skips when the log is empty, and refuses (fails loudly) when
 * a read table holds rows of a type the log has no events for, so it can never clear read-model rows it has
 * nothing to replay back into them.
 *
 * The application's startup initializer invokes the runners in their `ORDER` sequence (before the web server
 * accepts requests), so the import-before-rebuild order holds and a rebuild sees the events that the import
 * may have just added.
 *
 * The replay writes the ids and the `createdAt`/`updatedAt` from the event bodies. An entity's optimistic
 * locking version restarts from zero, which has no effect because nothing compares a version across a
 * rebuild.
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
        // A non-empty log can still fail to cover every read table (e.g. after flipping persistence.mode, the
        // log may hold events for some types but not others). Clearing and replaying then silently deletes the
        // rows of any type the log cannot replay, so refuse rather than lose data.
        val unreplayable = typesWithRowsButNoEvents()
        require(unreplayable.isEmpty()) {
            "Refusing the events-to-data rebuild: the read model has rows of type(s) " +
                "${unreplayable.joinToString()} with no events in the log, so a rebuild would delete them with " +
                "nothing to replay. Import the existing rows first " +
                "(campus-coffee.persistence.data-to-events-on-startup=true) or clear them deliberately."
        }
        clearReadTables()
        val events = eventRepository.findAllByOrderBySeqAsc()
        events.forEach { projector.apply(it) }
        log.info { "Rebuilt the read model from ${events.size} events in the log." }
    }

    /**
     * The entity type labels of the read tables that hold rows but have no events in the log, so a rebuild
     * could not restore them. Empty when every populated read table is covered by the log.
     */
    private fun typesWithRowsButNoEvents(): List<String> {
        val readTables: List<Pair<KClass<out DomainModel<*>>, JpaRepository<out Entity, UUID>>> =
            listOf(
                User::class to userRepository,
                Pos::class to posRepository,
                Review::class to reviewRepository,
                ReviewApproval::class to reviewApprovalRepository
            )
        return readTables
            .filter { (_, repository) -> repository.count() > 0L }
            .map { (type, _) -> requireNotNull(type.simpleName) }
            .filterNot { eventRepository.existsByEntityType(it) }
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
