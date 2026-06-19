package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.Application
import de.seuhd.campuscoffee.api.mapper.PosDtoMapper
import de.seuhd.campuscoffee.api.mapper.ReviewDtoMapper
import de.seuhd.campuscoffee.api.mapper.UserDtoMapper
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.configureClient
import de.seuhd.campuscoffee.tests.SystemTestUtils.configurePostgresContainers
import de.seuhd.campuscoffee.tests.SystemTestUtils.getPostgresContainer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Abstract base class for system tests. Sets up the Spring Boot test context, manages the PostgreSQL
 * testcontainer, and configures the [RestTestClient][de.seuhd.campuscoffee.tests.SystemTestUtils]. The id
 * generator is the seeded (deterministic) one, configured via `campus-coffee.id.seed`.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
abstract class AbstractSystemTest {
    @Autowired
    protected lateinit var posService: PosService

    @Autowired
    protected lateinit var userService: UserService

    @Autowired
    protected lateinit var reviewService: ReviewService

    @Autowired
    protected lateinit var reviewApprovalDataService: ReviewApprovalDataService

    @Autowired
    protected lateinit var posDtoMapper: PosDtoMapper

    @Autowired
    protected lateinit var userDtoMapper: UserDtoMapper

    @Autowired
    protected lateinit var reviewDtoMapper: ReviewDtoMapper

    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun beforeEach() {
        clearAll()
        // seed the fixture users (with known passwords and their role sets) so a write request can
        // authenticate over HTTP Basic; every write request now requires an authenticated user
        seededAuthUsers = TestFixtures.createUserFixtures(userService)
        configureClient(port)
    }

    @AfterEach
    fun afterEach() {
        clearAll()
    }

    // Clears in foreign-key order: approvals reference reviews and users, reviews reference POS and users.
    // Clearing approvals explicitly also removes their events in event-sourcing mode (where it is harmless
    // in relational mode), so the same base resets both backends.
    private fun clearAll() {
        reviewApprovalDataService.clear()
        reviewService.clear()
        posService.clear()
        userService.clear()
    }

    /** The fixture users seeded before each test, available so a test can act as a specific one. */
    protected lateinit var seededAuthUsers: List<de.seuhd.campuscoffee.domain.model.objects.User>

    protected companion object {
        // Shared across all system tests: a val in the companion object is a single instance, started once.
        protected val postgresContainer: PostgreSQLContainer<*> = getPostgresContainer().apply { start() }

        // Spring only invokes @DynamicPropertySource on a static method; @JvmStatic makes this
        // companion function a real JVM static so the Postgres container's JDBC settings reach the Spring context.
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            configurePostgresContainers(registry, postgresContainer)
        }
    }
}
