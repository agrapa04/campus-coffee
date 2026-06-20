package de.seuhd.campuscoffee.data.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The two interchangeable persistence modes.
 *
 * - [RELATIONAL]: writes go straight to the relational tables; there is no event log.
 * - [EVENT_SOURCING]: the default. The event log is the source of truth and the tables are a read model
 *   projected from it. Each write appends an event and projects it in one transaction.
 */
enum class PersistenceMode {
    RELATIONAL,
    EVENT_SOURCING
}

/**
 * Configuration for the persistence layer, bound from `campus-coffee.persistence.*`. An unknown [mode]
 * value fails startup, because it cannot bind to the [PersistenceMode] enum.
 *
 * @property mode the persistence mode; event sourcing by default.
 * @property dataToEventsOnStartup when true, seed the event log from the existing relational rows on
 *   startup (import an existing database into the log). Appends one INSERT event per row, idempotently.
 * @property eventsToDataOnStartup when true, rebuild the relational tables from the event log on startup
 *   (clear the tables and replay every event). Only active in event sourcing mode.
 */
@ConfigurationProperties(prefix = "campus-coffee.persistence")
data class PersistenceProperties(
    val mode: PersistenceMode = PersistenceMode.EVENT_SOURCING,
    val dataToEventsOnStartup: Boolean = false,
    val eventsToDataOnStartup: Boolean = false
) {
    companion object {
        /** The mode property, referenced by the decorators' `@ConditionalOnProperty`. */
        const val MODE_PROPERTY = "campus-coffee.persistence.mode"

        /** The raw [MODE_PROPERTY] value that activates event sourcing (binds to [PersistenceMode.EVENT_SOURCING]). */
        const val EVENT_SOURCING_MODE = "event-sourcing"
    }
}
