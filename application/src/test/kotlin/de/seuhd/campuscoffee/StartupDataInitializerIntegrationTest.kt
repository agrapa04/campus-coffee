package de.seuhd.campuscoffee

import de.seuhd.campuscoffee.domain.ports.StartupTask
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.tests.SystemTestUtils.configurePostgresContainers
import de.seuhd.campuscoffee.tests.SystemTestUtils.getPostgresContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Verifies the behavior the startup orchestrator exists for: the startup tasks finish before the embedded
 * web server starts, so the API is never served before its data is loaded. It boots the full application
 * with the fixture load and both event sourcing migrations enabled, and the probe records how many users
 * exist at the moment the web server is initialized (an event that fires after the connectors bind). Under
 * the previous `ApplicationReadyEvent` trigger the fixtures loaded only after that point, so the probe would
 * see an empty database and the first assertion would fail; the [StartupDataInitializer] running during
 * context refresh is what makes it pass.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "campus-coffee.fixtures.load-on-startup=true",
        "campus-coffee.persistence.mode=event-sourcing",
        "campus-coffee.persistence.data-to-events-on-startup=true",
        "campus-coffee.persistence.events-to-data-on-startup=true"
    ]
)
@Import(StartupDataInitializerIntegrationTest.ProbeConfig::class)
class StartupDataInitializerIntegrationTest {
    @Autowired
    private lateinit var probe: WebServerInitProbe

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var fixtureStartupLoader: FixtureStartupLoader

    @Autowired
    private lateinit var startupTasks: List<StartupTask>

    @Test
    fun `the fixture data is loaded before the web server starts accepting requests`() {
        val loadedUsers = userService.getAll().size
        assertThat(loadedUsers).`as`("the fixtures loaded at all").isPositive()
        // all of them were already present when the web server came up, not loaded afterwards
        assertThat(probe.userCountAtWebServerInit).isEqualTo(loadedUsers)
    }

    @Test
    fun `the fixture load is the last startup task, after the import and rebuild migrations`() {
        val migrations = startupTasks.filter { it !== fixtureStartupLoader }
        // both event sourcing migration runners are present (their flags are on)
        assertThat(migrations).hasSize(2)
        assertThat(migrations).allSatisfy {
            assertThat(it.order).isLessThan(fixtureStartupLoader.order)
        }
    }

    @TestConfiguration
    class ProbeConfig {
        @Bean
        fun webServerInitProbe(userService: UserService) = WebServerInitProbe(userService)
    }

    /** Records the user count at web-server-initialized time, i.e. as the server starts accepting requests. */
    class WebServerInitProbe(
        private val userService: UserService
    ) {
        var userCountAtWebServerInit: Int = -1
            private set

        @EventListener(WebServerInitializedEvent::class)
        fun onWebServerInitialized() {
            userCountAtWebServerInit = userService.getAll().size
        }
    }

    companion object {
        // started once for this test class, mirroring AbstractSystemTest
        private val postgresContainer: PostgreSQLContainer<*> = getPostgresContainer().apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            configurePostgresContainers(registry, postgresContainer)
        }
    }
}
