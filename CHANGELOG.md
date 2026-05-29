# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

- Migrate the build from Maven to Gradle (Kotlin DSL): a `gradle/libs.versions.toml` version catalog and `build-logic` convention plugins replace the parent POM; the `coverage` module becomes a Gradle subproject using `jacoco-report-aggregation` with a `JacocoCoverageVerification` gate (unchanged 90% line / 80% branch). Gradle is provisioned via `mise` (no wrapper) and the `pom.xml` files are removed.
- Bump the JDK from 21 to 25 (current LTS).
- Upgrade Spring Boot 3.5.8 to 4.0.6 (Spring Framework 7) and springdoc to 3.0.1. Drop Spring Cloud and migrate the OpenStreetMap client from `@FeignClient` to a Spring `@HttpExchange` declarative client over `RestClient`. Adjust for Boot 4 changes: add the `spring-boot-flyway` autoconfiguration module (autoconfig is now modular), pin a single JUnit 6 via an enforced `junit-bom` (cucumber pulls JUnit Platform 1.x, which clashes with Spring 7), migrate the custom sequence generator to Hibernate 7's `GeneratorCreationContext` SPI so it again sets the sequence increment to 1 to match the Flyway sequences (Hibernate 7 now validates the entity increment against the database), and pin Testcontainers (no longer in the Boot BOM).
- Replace RestAssured with Spring Framework 7's `RestTestClient` in the system and acceptance HTTP tests, removing RestAssured and its transitive Groovy dependency from the test classpath.
- Scope `spring-boot-starter-web` to `api`/`application` rather than every module, build subprojects in parallel, and drop the redundant plain application jar.
- Extend `Dockerfile` to build the application with Gradle and then copy the created JAR file to the image.
- Modify `compose.yaml` to work with Google Cloud Build and Could Run.
- Extend `mise.toml` to include dependencies for Google Cloud.
- Update `README.md` to explain deployment to Google Cloud Run.
- Add JaCoCo code coverage with a new `coverage` module that aggregates execution data across modules, so coverage from the integration and system tests in `application` is attributed to the `domain`, `api`, and `data` classes they test.
- Enforce a line and branch coverage gate during `gradle build` (the `check` task), run it in CI, and upload the coverage reports as a build artifact.
- Add opt-in PITest mutation testing (`gradle :<module>:pitest -Pmutation`): each module mutates its own classes against its own tests (`domain.*`, `api.*`, `data.*`), and `application` additionally mutates `api.*`/`data.*` against the system and acceptance tests via additional mutable code paths (the Gradle equivalent of Maven's `crossModule`). Each module writes its own report under `build/reports/pitest`. Generated `*MapperImpl` classes are excluded. The mutator group is selectable with `-Ppitest.mutators=DEFAULTS|STRONGER|ALL`.
- Strengthen the unit tests using surviving mutants as a worklist: add `OsmAmenityTest`, `CrudDataServiceImplTest`, `UserServiceTest`, an OSM-node-to-POS field-mapping assertion, and a filter-miss 404 system test. Derive test boundary values from the `Pos` postal-code bounds and the OSM default description instead of duplicating literals.
- Migrate the production code and the tests from Java to Kotlin, module by module (`domain` → `api` → `data` → `application`). The former record types become `data class`es (the `domain` models, the `api` DTOs, and `ErrorResponse`) with named-argument constructors and `copy()` in place of Lombok builders; the JPA entities use the `kotlin-jpa` (no-arg/all-open) plugin instead of `@NoArgsConstructor`/`@Getter`/`@Setter`; and the MapStruct DTO and entity mappers run via `kapt`, with `HouseNumberConverter` and the `@Mapping(expression=...)` logic preserved. `TestFixtures`, the `GlobalExceptionHandler`, the OpenAPI customizers, the custom sequence generators, and every unit, integration, system, acceptance, and architecture test are Kotlin as well. Add `kotlin-conventions` (Kotlin 2.3.20, `jvmTarget` 25, the Spring all-open plugin, `kotlin-reflect`), `kotlin-jpa-conventions`, and `kotlin-kapt-conventions` convention plugins, and generate Spring configuration metadata for `@ConfigurationProperties` via `kapt`. Remove Lombok entirely (the Gradle plugin, the Java/Kotlin Lombok dependencies, `lombok.config`, and the JSpecify nullability annotations) — Kotlin's nullable types express nullability natively. The Kotlin DTOs deserialize via `jackson-module-kotlin`, and the Mockito-based tests use `mockito-kotlin`.
- Tidy the Kotlin idioms surfaced by a post-migration review: `TestFixtures` returns its immutable fixture data classes directly instead of deep-cloning them with `SerializationUtils` (removing the last use of `commons-lang3`, now dropped, and of `Serializable` on `DomainModel`); `java.util.Optional` is replaced with native nullable types across the domain amenity enum, the OpenAPI parameter model, the POS/User repositories (`findByIdOrNull`), and the data-service lookups; the OpenAPI success-schema builder becomes a `when` over the return type; an `Identifiable.persistedId` accessor names the must-be-persisted invariant in place of scattered `id!!`; and `CrudDataService.upsert`'s parameter plus the controller override parameters are renamed to match their supertypes (clearing the named-argument compiler warnings).
- Add ktlint (the jlleitschuh Gradle plugin) to format and lint the Kotlin code against the official Kotlin style, configured via a root `.editorconfig` (4-space indent, 120-column lines, trailing commas disabled). `ktlintCheck` runs as part of `check`, so `gradle build` and CI fail on formatting violations; `gradle ktlintFormat` applies the fixes. `.gitattributes` gains `eol=lf` line-ending normalization.

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
