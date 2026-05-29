package de.seuhd.campuscoffee

import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Loads initial data into the application when running in the "dev" profile.
 */
@Component
@Profile("dev")
class LoadInitialData(
    private val posService: PosService,
    private val userService: UserService,
    private val reviewService: ReviewService,
) : InitializingBean {

    override fun afterPropertiesSet() {
        log.info("Deleting existing data...")
        reviewService.clear()
        posService.clear()
        userService.clear()
        log.info("Loading initial data...")
        val userFixtures = TestFixtures.createUserFixtures(userService)
        log.info("Created {} users.", userFixtures.size)
        val posFixtures = TestFixtures.createPosFixtures(posService)
        log.info("Created {} POS.", posFixtures.size)
        val reviewFixtures = TestFixtures.createReviewFixtures(reviewService)
        log.info("Created {} reviews.", reviewFixtures.size)
        log.info("Initial data loaded successfully.")
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LoadInitialData::class.java)
    }
}
