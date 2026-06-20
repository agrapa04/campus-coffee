package de.seuhd.campuscoffee.tests.acceptance

import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.configureClient
import de.seuhd.campuscoffee.tests.SystemTestUtils.configurePostgresContainers
import de.seuhd.campuscoffee.tests.SystemTestUtils.getPostgresContainer
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Single Spring and Cucumber configuration shared by all acceptance step definitions. Cucumber allows
 * only one [CucumberContextConfiguration], so the step classes ([CucumberPosSteps], [CucumberReviewSteps])
 * hold step definitions only and rely on the context, container, and cleanup hooks defined here. Pins the
 * relational persistence mode so the acceptance suite runs against the relational backend regardless of the
 * application's default mode (event sourcing).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CucumberContextConfiguration
@TestPropertySource(properties = ["campus-coffee.persistence.mode=relational"])
class CucumberSpringConfiguration(
    private val posService: PosService,
    private val userService: UserService,
    private val reviewService: ReviewService
) {
    @LocalServerPort
    private var port: Int = 0

    @Before
    fun beforeEach() {
        // reviews reference POS and users via foreign keys, so they must be cleared first
        reviewService.clear()
        posService.clear()
        userService.clear()
        // seed the fixture users (with known passwords and roles) so a POS write request in a scenario can
        // authenticate as a moderator; the feature's own users are registered by the step definitions
        TestFixtures.createUserFixtures(userService)
        configureClient(port)
    }

    @After
    fun afterEach() {
        reviewService.clear()
        posService.clear()
        userService.clear()
    }

    companion object {
        // share one testcontainers instance across all acceptance tests
        private val postgresContainer: PostgreSQLContainer<*> = getPostgresContainer().apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            configurePostgresContainers(registry, postgresContainer)
        }
    }
}
