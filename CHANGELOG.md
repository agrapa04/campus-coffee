# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

- Restrict the Spring Boot Actuator endpoints. `/actuator/health` stays publicly readable (it reports only an `UP`/`DOWN` status, no component detail), `/actuator/metrics` now requires `ADMIN`, and the access rules precede the public `GET` catch-all in `SecurityConfig` so the catch-all no longer exposes them anonymously. The `prod` profile drops `env` from the exposed set, so the environment endpoint is unreachable in production (it stays exposed in `dev`).

## [0.2.0] - 2026-06-17

- Add authentication and authorization. Every write request requires an authenticated principal; the POS directory and reviews stay publicly readable, while user data (login names, emails, roles) is readable only by that user or an `ADMIN` (listing all users is admin-only). Authentication is stateless via HTTP Basic or a JWT bearer token, both resolving to the same principal (login name + `ROLE_*` authorities); `SecurityConfig` disables CSRF and server-side sessions and renders auth failures as the app's JSON `ErrorResponse` (`401`/`403`). A new Flyway migration (`V7`) adds the `password_hash` column and the `user_roles` table, and passwords are hashed with BCrypt via a `CampusUserDetailsService`.
- Add the three independent roles `USER`, `MODERATOR`, and `ADMIN` (no hierarchy): POS curation requires `MODERATOR`, deleting a user requires `ADMIN`, and every other write request requires a login. `USER` is the base role, granted at registration and always retained — an admin may grant or revoke `MODERATOR`/`ADMIN` but cannot strip `USER`. Registration is open and always yields a plain `USER`, so a client cannot self-assign a privileged role.
- Derive the acting user from the authenticated principal instead of the request body: a `CurrentUserProvider` in the `api` layer turns the login name into a domain `User`, so `POST /api/reviews` takes its author and the approve endpoint its approver from the caller (the `authorId` body field and the `user_id` query parameter are gone, and a body still carrying `authorId` is rejected with `400`). Per-target rules live in the domain via a new `ForbiddenException` (mapped to `403`): a review's author or a `MODERATOR` may edit/delete it, a user edits only their own account (an admin edits anyone), and a non-admin may read only their own user record.
- Enforce one approval per user, closing the previously documented repeated-approval hole: approvals are recorded per user (a `ReviewApproval` keyed by review and approver), so a user cannot approve the same review twice (`409`) and cannot approve their own review (`400`).
- Add a stateless JWT bearer-token flow: `POST /api/auth/token` exchanges credentials for a short-lived (15-minute) HMAC-signed token whose `roles` claim maps to authorities, so the same authorization rules apply under Bearer as under Basic. The signing secret comes from `JWT_SECRET`; the `dev` profile has an insecure fallback, while the `prod` profile has none (startup fails if it is unset).
- Require a password on registration (minimum 8 characters, scoped to a create-only Bean Validation group so updates may omit it and keep the stored hash); no response ever serializes a password or hash.
- Add a `prod` profile and `compose.prod.yaml` for a public Google Cloud Run deployment: authentication is enforced, Swagger and the `/api/dev` endpoints are off, and the fixture data loads on startup (`campus-coffee.fixtures.load-on-startup`, on by default in `prod`). `gcloud beta run compose up` runs the app and PostgreSQL as sidecar containers sharing one network namespace, so the datasource host defaults to `localhost` (`DB_HOST`; Docker Compose uses the `db` service name); the JWT secret is set and public invocation granted after the first deploy. The `README.md` and `INSTRUCTOR.md` document the flow.
- Build the Docker image via `mise` instead of a hand-pinned base image: the build stage runs from `jdxcode/mise` and provisions the JDK and Gradle from `mise.toml` (matching CI), leaving only the runtime stage's `eclipse-temurin` tag as a hand-maintained Java pin. A Dependabot `docker` ecosystem keeps the base images current, ignoring that runtime tag (which `scripts/check-toolchain-versions.sh` holds in sync).

## [0.1.0] - 2026-06-16

- Migrate the build from Maven to Gradle (Kotlin DSL): a `gradle/libs.versions.toml` version catalog and `build-logic` convention plugins (in the `de.seuhd.campuscoffee` package) replace the parent POM; the `coverage` module becomes a Gradle subproject using `jacoco-report-aggregation` with a `JacocoCoverageVerification` gate (90% line / 80% branch). Bump the JDK from 21 to 25, scope `spring-boot-starter-web` to `api`/`application`, build subprojects in parallel, and drop the redundant plain jar. Gradle is provisioned via `mise` (no wrapper); the `pom.xml` files and stale `target/` output are removed.
- Upgrade Spring Boot 3.5.8 to 4.0.6 (Spring Framework 7) and springdoc to 3.0.1. Drop Spring Cloud and migrate the OpenStreetMap client from `@FeignClient` to a Spring `@HttpExchange` declarative client over `RestClient`. Adjust for Boot 4: add the `spring-boot-flyway` autoconfiguration module, pin a single JUnit 6 via an enforced `junit-bom` (cucumber pulls JUnit Platform 1.x, which clashes with Spring 7), migrate the custom sequence generator to Hibernate 7's `GeneratorCreationContext` SPI (which again sets the sequence increment to 1 to match Flyway), and pin Testcontainers. Replace RestAssured with Spring 7's `RestTestClient` in the system and acceptance HTTP tests, removing RestAssured and its transitive Groovy dependency.
- Migrate the production code and the tests from Java to Kotlin, module by module (`domain` → `api` → `data` → `application`). The former records become `data class`es (domain models, DTOs, `ErrorResponse`) with named arguments and `copy()` in place of Lombok builders; the JPA entities use the `kotlin-jpa` (no-arg/all-open) plugin; and the MapStruct mappers run via `kapt`, preserving `HouseNumberConverter` and the `@Mapping(expression=...)` logic. `TestFixtures`, the `GlobalExceptionHandler`, the OpenAPI customizers, the sequence generators, and every test are Kotlin too. Add `kotlin-conventions` (Kotlin 2.4.0, `jvmTarget` 25, the Spring all-open plugin, `kotlin-reflect`), `kotlin-jpa-conventions`, and `kotlin-kapt-conventions` convention plugins, and generate Spring configuration metadata for `@ConfigurationProperties` via `kapt`. Remove Lombok entirely (and the JSpecify nullability annotations), since Kotlin's nullable types express nullability natively. DTOs deserialize via `jackson-module-kotlin`; the Mockito tests use `mockito-kotlin`.
- Containerize and deploy to Google Cloud Run: the `Dockerfile` provisions the JDK and Gradle from `mise.toml` (the same pins as CI) on a `jdxcode/mise` build stage and copies the JAR onto an `eclipse-temurin` runtime, `compose.yaml` works with Google Cloud Build and Cloud Run, `mise.toml` adds the Google Cloud dependencies, and `README.md` documents the deployment.
- Add JaCoCo code coverage via a new `coverage` module that aggregates execution data across modules (so the integration and system tests in `application` count toward `domain`/`api`/`data`), and enforce a line/branch gate during `gradle build` and in CI, uploading the reports as an artifact. Add opt-in PITest mutation testing (`gradle :<module>:pitest -Pmutation`): each module mutates its own classes, and `application` additionally mutates `api`/`data` against the system and acceptance tests (generated `*MapperImpl` classes excluded). Strengthen the unit tests using surviving mutants as a worklist.
- Tidy Kotlin idioms and enforce style: add ktlint, gated via `check` and configured by a root `.editorconfig` (with `.gitattributes` normalizing line endings to LF); return the immutable fixtures directly (dropping `commons-lang3` and `Serializable`), replace `java.util.Optional` with native nullables (`findByIdOrNull`), and add an `Identifiable.persistedId` accessor; drop `@JvmStatic` left over from Java interop; convert the Cucumber glue to constructor injection; and adopt backtick test-method names with a consistent structure (documented in `CLAUDE.md`), using the reified `returnResult<T>()` in the system tests.
- Add detekt `2.0.0-alpha.5` for Kotlin static analysis, applied through the `kotlin-conventions` convention plugin and gated via `check` (the build fails on new findings). A per-module `detekt-baseline.xml` grandfathers the remaining findings (3, in the `data` exception-mapping code); a swallowed-exception finding it surfaced is fixed by chaining the optimistic-lock cause into `ConcurrentUpdateException`. detekt requires the exact Kotlin version it was built against, so Kotlin is pinned at 2.4.0; the stable detekt 1.x line does not support Kotlin 2.4.
- Tighten the web layer: centralize the `/api` base path in an `ApiPathConfig` `WebMvcConfigurer`; make `GlobalExceptionHandler` extend `ResponseEntityExceptionHandler` so standard Spring MVC exceptions map to their correct status codes (unknown path → 404, wrong method → 405, unsupported/unacceptable media type → 415/406, missing parameter → 400) instead of 500; and serve the API as JSON only by dropping the XML converter (via Spring 7's `HttpMessageConverters` builder; OSM parsing keeps its own `XmlMapper`). Bind `osm.api.base-url` through an `OsmApiProperties` `@ConfigurationProperties` class (which generates configuration metadata) rather than `@Value`.
- Replace the automatic data load on startup with on-demand, dev-only endpoints on `DevController` (`GET`/`PUT`/`DELETE /api/dev/data`, registered only in the `dev` profile; `PUT` clears and reseeds idempotently). The application no longer seeds data on startup in any profile, and the database persists across restarts. Removes `LoadInitialData`.
- Add a Dependabot config (`.github/dependabot.yml`) that weekly checks the GitHub Actions, the Gradle dependencies and plugins (through the version catalog), and the Dockerfile base images, grouping minor and patch bumps into one pull request and keeping major upgrades separate for review; the runtime `eclipse-temurin` tag stays under `scripts/check-toolchain-versions.sh` and is ignored.
- Add a weekly `mise-outdated` GitHub Actions workflow that runs `mise outdated` and opens (or updates) a tracking issue when the mise-managed tools (JDK, Gradle, gcloud, python) fall behind, since Dependabot has no mise ecosystem.
- Move the Spring Security setup out of the `application` module into the `api/security` package, joining the `CurrentUserProvider` already there, so the api module owns the access rules that define its interface. The supporting beans (`JwtConfig`, `JwtProperties`, `JsonAuthenticationEntryPoint`, and the `UserDetailsService` adapter, renamed `CampusUserDetailsService` to `DomainUserDetailsService`) and the `SecurityConfig` exercise stub all move. The ArchUnit application layer drops the now-empty `security` package. The exercise content is unchanged.
- Bind one `RestTestClient` per server port in `SystemTestUtils` (`computeIfAbsent`) instead of rebuilding it on every test, so the system tests stay stable when run in a single JVM without fork-level parallelism (`-PtestForks=1`).
- Rename `ApiPathConfig` to `ApiWebConfig`. The `WebMvcConfigurer` does more than the base path (it also drops the XML message converter and pins the JSON charset to UTF-8), so "Web" names its scope. No behavior change.

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
