# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

- Migrate the build from Maven to Gradle (Kotlin DSL): a `gradle/libs.versions.toml` version catalog and `build-logic` convention plugins (in the `de.seuhd.campuscoffee` package) replace the parent POM; the `coverage` module becomes a Gradle subproject using `jacoco-report-aggregation` with a `JacocoCoverageVerification` gate (90% line / 80% branch). Bump the JDK from 21 to 25, scope `spring-boot-starter-web` to `api`/`application`, build subprojects in parallel, and drop the redundant plain jar. Gradle is provisioned via `mise` (no wrapper); the `pom.xml` files and stale `target/` output are removed.
- Upgrade Spring Boot 3.5.8 to 4.0.6 (Spring Framework 7) and springdoc to 3.0.1. Drop Spring Cloud and migrate the OpenStreetMap client from `@FeignClient` to a Spring `@HttpExchange` declarative client over `RestClient`. Adjust for Boot 4: add the `spring-boot-flyway` autoconfiguration module, pin a single JUnit 6 via an enforced `junit-bom` (cucumber pulls JUnit Platform 1.x, which clashes with Spring 7), migrate the custom sequence generator to Hibernate 7's `GeneratorCreationContext` SPI (which again sets the sequence increment to 1 to match Flyway), and pin Testcontainers. Replace RestAssured with Spring 7's `RestTestClient` in the system and acceptance HTTP tests, removing RestAssured and its transitive Groovy dependency.
- Migrate the production code and the tests from Java to Kotlin, module by module (`domain` → `api` → `data` → `application`). The former records become `data class`es (domain models, DTOs, `ErrorResponse`) with named arguments and `copy()` in place of Lombok builders; the JPA entities use the `kotlin-jpa` (no-arg/all-open) plugin; and the MapStruct mappers run via `kapt`, preserving `HouseNumberConverter` and the `@Mapping(expression=...)` logic. `TestFixtures`, the `GlobalExceptionHandler`, the OpenAPI customizers, the sequence generators, and every test are Kotlin too. Add `kotlin-conventions` (Kotlin 2.3.20, `jvmTarget` 25, the Spring all-open plugin, `kotlin-reflect`), `kotlin-jpa-conventions`, and `kotlin-kapt-conventions` convention plugins, and generate Spring configuration metadata for `@ConfigurationProperties` via `kapt`. Remove Lombok entirely (and the JSpecify nullability annotations), since Kotlin's nullable types express nullability natively. DTOs deserialize via `jackson-module-kotlin`; the Mockito tests use `mockito-kotlin`.
- Containerize and deploy to Google Cloud Run: the `Dockerfile` builds the app with Gradle and copies the JAR, `compose.yaml` works with Google Cloud Build and Cloud Run, `mise.toml` adds the Google Cloud dependencies, and `README.md` documents the deployment.
- Add JaCoCo code coverage via a new `coverage` module that aggregates execution data across modules (so the integration and system tests in `application` count toward `domain`/`api`/`data`), and enforce a line/branch gate during `gradle build` and in CI, uploading the reports as an artifact. Add opt-in PITest mutation testing (`gradle :<module>:pitest -Pmutation`): each module mutates its own classes, and `application` additionally mutates `api`/`data` against the system and acceptance tests (generated `*MapperImpl` classes excluded). Strengthen the unit tests using surviving mutants as a worklist.
- Tidy Kotlin idioms and enforce style: add ktlint, gated via `check` and configured by a root `.editorconfig` (with `.gitattributes` normalizing line endings to LF); return the immutable fixtures directly (dropping `commons-lang3` and `Serializable`), replace `java.util.Optional` with native nullables (`findByIdOrNull`), and add an `Identifiable.persistedId` accessor; drop `@JvmStatic` left over from Java interop; convert the Cucumber glue to constructor injection; and adopt backtick test-method names with a consistent structure (documented in `CLAUDE.md`), using the reified `returnResult<T>()` in the system tests.
- Tighten the web layer: centralize the `/api` base path in an `ApiPathConfig` `WebMvcConfigurer`; make `GlobalExceptionHandler` extend `ResponseEntityExceptionHandler` so standard Spring MVC exceptions map to their correct status codes (unknown path → 404, wrong method → 405, unsupported/unacceptable media type → 415/406, missing parameter → 400) instead of 500; and serve the API as JSON only by dropping the XML converter (via Spring 7's `HttpMessageConverters` builder; OSM parsing keeps its own `XmlMapper`). Bind `osm.api.base-url` through an `OsmApiProperties` `@ConfigurationProperties` class (which generates configuration metadata) rather than `@Value`.
- Replace the automatic data load on startup with on-demand, dev-only endpoints on `DevController` (`GET`/`PUT`/`DELETE /api/dev/data`, registered only in the `dev` profile; `PUT` clears and reseeds idempotently). The application no longer seeds data on startup in any profile, and the database persists across restarts. Removes `LoadInitialData`.

## [0.0.5] - 2025-12-09

- Add review controller, services, related classes, and tests (exercise 7.1).
- Add PosTest to showcase a simple unit test. 

## [0.0.4] - 2025-11-28

### Added

- Add new interface `Identifiable<T>` for entities that have a unique identifier (required by some of the new generic super classes/interfaces).
- Add new abstract base class `Dto<ID>`, which defines the ID and time stamps, convert DTO classes into classes (instead of record, which don't support inheritance).
- Add new interfaces for the domain model (`DomainModel<ID>`) and entities (`BaseEntity<ID>`) that make the corresponding objects identifiable.
- Implement `UserController`, `UserService`, and related user classes and mappers (exercise 6.1).
- Add new custom OpenAPI annotations to be used instead of repetitive OpenAPI annotations.

### Changed

- Move common logic from `PosController` and `UserController` into a generic `CrudController`.
- Move common OpenAPI annotations to `CrudController`.
- Move common logic from `PosDtoMapper` and `UserDtoMapper` into a generic `DtoMapper`.
- Move common logic from `PosService` and `UserService` into a generic `CrudService`.
- Move common logic from `PosServiceImpl` and `UserServiceImpl` to generic `CrudServiceImpl`.
- Move common logic from `PosEntityMapper` and `UserEntityMapper` to generic `EntityMapper`.
- Move common logic from `PosDataService` and `UserDataService` to generic `DataService`.
- Move common logic from `PosDataServiceImpl` and `UserDataServiceImpl` to generic `CrudDataServiceImpl`.
- Generalize conversion of database uniqueness constraints to domain exceptions.
- Generalize and refactor exception types
- Simplify GlobalExceptionHandler

### Deleted

## [0.0.3] - 2025-11-21

### Added

- Add OpenAPI annotations to POS API, activate Swagger UI in dev profile.
- Add delete endpoint to POS API to delete a POS by ID (see demo video).
- Add ArchUnit test for hexagonal architecture.
- Add bean validation to `PosDto`.
- Add `UserDataService`, `UserRepository`, class stubs, and test fixtures to enable implementation of `UserService` and `UserController` (preparation for exercise 6.1).
- Add `api/pos/filter` endpoint to POS API to filter by POS name (exercise 5.1).
- Add Cucumber scenarios and step definitions (exercise 5.2).

### Changed

- Refactored helper and util methods as well as exception classes.
- Restructured test cases in application module.
- Removed conflicting `commons-lang3` dependency version.
- Modify GitHub Actions workflow to trigger build for feature branches as well (exercise 3.2).

### Removed

- n/a

## [0.0.2] - 2025-11-12

### Added

- Add Cucumber dependencies, test runner, and examples (preparation for exercise 5.1).
- Add `Dockerfile` and `compose.yaml` to allow interested students to run the application in a Docker container.
- Add Feign client to interact with OpenStreetMap API (exercise 4.1).
- Add functionality to fetch data from OSM nodes to `PosDataServiceImpl` (exercise 4.1).
- Add conversion of OSM data to POS entities to `PosServiceImpl` (exercise 4.1).

### Changed

- Modify OSM import endpoint to include campus type (preparation for exercise 4.1).
- Fix Surfire configuration resulting in a warning.
- Use JRE instead of JDK base image in `Dockerfile` to reduce image size.
- Move test dependencies to `test` scope to reduce size of `application` JAR file.

### Removed

- n/a

## [0.0.1] - 2025-11-04

### Added

- Add new `POST` endpoint `/api/pos/import/osm/{nodeId}` that allows API users to import a `POS` based on an OpenStreetMap node (preparation for exercise 4.1).
- Add example of new OSM import endpoint to `README` file (preparation for exercise 4.1).

### Changed

- Extend `PosService` interface by adding a `importFromOsmNode` method (preparation for exercise 4.1).
- Fix broken test case in `PosSystemTests` (exercise 3.2).
- Extend GitHub Actions triggers to include pushes to feature branches (exercise 3.2).

### Removed

- n/a
