package de.seuhd.campuscoffee.tests.acceptance;

import de.seuhd.campuscoffee.domain.ports.api.PosService;
import de.seuhd.campuscoffee.domain.ports.api.ReviewService;
import de.seuhd.campuscoffee.domain.ports.api.UserService;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static de.seuhd.campuscoffee.tests.SystemTestUtils.configurePostgresContainers;
import static de.seuhd.campuscoffee.tests.SystemTestUtils.getPostgresContainer;

/**
 * Single Spring and Cucumber configuration shared by all acceptance step definitions.
 * Cucumber allows only one {@link CucumberContextConfiguration}, so the step classes
 * ({@link CucumberPosSteps}, {@link CucumberReviewSteps}) hold step definitions only and rely on
 * the context, container, and cleanup hooks defined here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CucumberContextConfiguration
public class CucumberSpringConfiguration {
    static final PostgreSQLContainer<?> postgresContainer;

    static {
        // share one testcontainers instance across all acceptance tests
        postgresContainer = getPostgresContainer();
        postgresContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        configurePostgresContainers(registry, postgresContainer);
    }

    @Autowired
    protected PosService posService;

    @Autowired
    protected UserService userService;

    @Autowired
    protected ReviewService reviewService;

    @LocalServerPort
    private Integer port;

    @Before
    public void beforeEach() {
        // reviews reference POS and users via foreign keys, so they must be cleared first
        reviewService.clear();
        posService.clear();
        userService.clear();
        RestAssured.baseURI = "http://localhost:" + port;
    }

    @After
    public void afterEach() {
        reviewService.clear();
        posService.clear();
        userService.clear();
    }
}
