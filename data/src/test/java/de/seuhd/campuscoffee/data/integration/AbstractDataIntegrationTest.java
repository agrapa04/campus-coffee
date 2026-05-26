package de.seuhd.campuscoffee.data.integration;

import de.seuhd.campuscoffee.data.DataTestApplication;
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository;
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository;
import de.seuhd.campuscoffee.data.persistence.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for data layer integration tests. Boots the data layer against a real PostgreSQL
 * container with Flyway-managed schema, and clears the tables before each test.
 */
@SpringBootTest(classes = DataTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class AbstractDataIntegrationTest {

    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    static {
        postgresContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        // the OSM client is wired but never called in these tests
        registry.add("osm.api.base-url", () -> "http://localhost:1");
    }

    @Autowired
    protected PosRepository posRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ReviewRepository reviewRepository;

    @BeforeEach
    void clearDatabase() {
        // reviews reference POS and users via foreign keys, so they must be cleared first
        reviewRepository.deleteAllInBatch();
        posRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
