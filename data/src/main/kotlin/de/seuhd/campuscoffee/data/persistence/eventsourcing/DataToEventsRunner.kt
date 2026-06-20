package de.seuhd.campuscoffee.data.persistence.eventsourcing
import de.seuhd.campuscoffee.data.configuration.PersistenceMode
import de.seuhd.campuscoffee.data.configuration.PersistenceProperties
import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.mapper.ReviewApprovalEntityMapper
import de.seuhd.campuscoffee.data.mapper.ReviewEntityMapper
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper
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
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

/**
 * Imports an existing relational database into the event log on startup, when
 * `campus-coffee.persistence.data-to-events-on-startup` is true: reads the current rows and appends one
 * INSERT event per row, so a database populated before event sourcing was enabled gets a log it can later
 * be rebuilt from. Idempotent per type: a type whose log already holds events is skipped, so repeated
 * startups (or a startup after the fixtures already wrote events) do not duplicate the log.
 *
 * The rows are read in foreign-key order (users and POS, then reviews, then approvals) so a later
 * events-to-data replay applies them in an order where a review's POS and author already exist.
 *
 * It runs only in event-sourcing mode (matching [EventsToDataRunner]): importing rows in relational mode
 * would record a one-time snapshot that later write requests then diverge from, which a rebuild could
 * replay over current data. The application's startup initializer invokes the runners in their `ORDER`
 * sequence (before the web server accepts requests); this runs before the rebuild runner, so when both flags
 * are set the log is seeded from the existing rows before the rebuild replays it.
 */
@Component
@ConditionalOnProperty(name = ["campus-coffee.persistence.data-to-events-on-startup"], havingValue = "true")
class DataToEventsRunner(
    private val properties: PersistenceProperties,
    private val eventStore: EventStore,
    private val posRepository: PosRepository,
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository,
    private val reviewApprovalRepository: ReviewApprovalRepository,
    private val posMapper: PosEntityMapper,
    private val userMapper: UserEntityMapper,
    private val reviewMapper: ReviewEntityMapper,
    private val reviewApprovalMapper: ReviewApprovalEntityMapper
) : StartupTask {
    override val order = ORDER

    @Transactional
    override fun run() = importRowsAsEvents()

    @Transactional
    fun importRowsAsEvents() {
        if (properties.mode != PersistenceMode.EVENT_SOURCING) {
            log.info(
                "Skipping the data-to-events import: persistence mode is {}, not event-sourcing.",
                properties.mode
            )
            return
        }
        importType(User::class) { userRepository.findAll().map(userMapper::fromEntity) }
        importType(Pos::class) { posRepository.findAll().map(posMapper::fromEntity) }
        importType(Review::class) { reviewRepository.findAll().map(reviewMapper::fromEntity) }
        importType(ReviewApproval::class) { reviewApprovalRepository.findAll().map(reviewApprovalMapper::fromEntity) }
    }

    private fun importType(
        domainType: KClass<out DomainModel<*>>,
        readRows: () -> List<DomainModel<*>>
    ) {
        val entityType = eventStore.entityTypeOf(domainType)
        if (eventStore.hasEventsFor(entityType)) {
            log.info("Skipping {} import: the event log already has {} events.", entityType, entityType)
            return
        }
        val rows = readRows()
        rows.forEach { eventStore.appendInsert(it) }
        log.info("Imported {} {} rows into the event log as INSERT events.", rows.size, entityType)
    }

    companion object {
        /** Runs before [EventsToDataRunner], so the log is seeded before any rebuild replays it. */
        const val ORDER = 0
        private val log = LoggerFactory.getLogger(DataToEventsRunner::class.java)
    }
}
