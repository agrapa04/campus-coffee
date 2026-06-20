package de.seuhd.campuscoffee

import de.seuhd.campuscoffee.domain.ports.StartupTask
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Loads the fixture data on startup when `campus-coffee.fixtures.load-on-startup` is true (declared by
 * `FixturesProperties`) and the database has no users yet. The prod deployment uses this to populate a
 * fresh database, because the prod profile does not register the `/api/dev` endpoints that load the data
 * during local development.
 *
 * [StartupDataInitializer] runs this before the web server accepts requests, after any event-sourcing
 * import/rebuild migration, so the guard sees the rebuilt users and does not load the fixtures again, and the
 * API is never served before its data is loaded. It is the last startup task, so its [order] is the highest.
 */
@Component
@ConditionalOnProperty("campus-coffee.fixtures.load-on-startup", havingValue = "true")
class FixtureStartupLoader(
    private val userService: UserService,
    private val posService: PosService,
    private val reviewService: ReviewService,
    private val reviewApprovalDataService: ReviewApprovalDataService
) : StartupTask {
    override val order = ORDER

    override fun run() = loadOnStartup()

    fun loadOnStartup() {
        if (userService.getAll().isNotEmpty()) {
            log.info("Skipping the fixture load: the database already has users.")
            return
        }
        val (users, pos, reviews) =
            TestFixtures.loadAll(userService, posService, reviewService, reviewApprovalDataService)
        log.info("Loaded the fixture data on startup: {} users, {} POS, {} reviews.", users, pos, reviews)
    }

    private companion object {
        // runs after the event-sourcing import (0) and rebuild (100) startup tasks
        private const val ORDER = 200
        private val log = LoggerFactory.getLogger(FixtureStartupLoader::class.java)
    }
}
