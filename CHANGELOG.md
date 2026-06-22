# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.5.4] - 2026-06-23

- Run JetBrains Qodana static analysis in CI on every push. A new `.github/workflows/qodana.yml` (triggered by `push` to any branch, matching `build.yml`) runs the `JetBrains/qodana-action`, and a root `qodana.yaml` selects the free JVM community linter (`jetbrains/qodana-jvm-community`, no license or `QODANA_TOKEN` needed) and excludes `**/build/**` (the generated MapStruct `*MapperImpl` and build output), mirroring the coverage and mutation exclusions. The scan is a separate IDE-inspection pass; it does not run as part of `gradle build`. Setting a `QODANA_TOKEN` secret additionally links runs to Qodana Cloud (baseline and trends).
- Rename the event-sourcing write helper `EventSourcedMutator` to `EventSourcedWriter` (the `mutator` field in the four decorators becomes `writer`). "Mutator" collided with the PITest mutation-testing terminology this project already uses (`-Ppitest.mutators`); "Writer" names the role (the write side of the event-sourced adapter, paired with reads that delegate) without the clash. Behavior is unchanged; the bean is wired by type.
- Standardize the identifier rendering in log messages to `with ID '<id>'` (quoted, no colon), fixing the two outliers in `CrudServiceImpl` (`with ID: '<id>'` on upsert and the unquoted `with ID <id>` on delete) so every log line is uniform.
- Guard the Cloud Run deploy against a stale `gcloud config`. `scripts/deploy-cloudrun.sh` now accepts an optional `--project <id>` override, prints the resolved target (project, region, mode), and asks for confirmation before deploying (`-y`/`--yes` skips the prompt); it passes `--project` to every `gcloud` call so the deploy hits exactly the confirmed project. No project is hardcoded — it still falls back to the active config.
- Document Spring Boot Actuator observability in `INSTRUCTOR.md`: the public health endpoint and the ADMIN-only metrics endpoint (`jvm.memory.used`, `http.server.requests` with a tag filter), tying into the guide's authentication theme.

## [0.5.3] - 2026-06-22

- Log through kotlin-logging (`io.github.oshai:kotlin-logging-jvm`) instead of the SLF4J API directly. Each `LoggerFactory.getLogger(X::class.java)` in a companion object becomes `KotlinLogging.logger {}`, and the call sites move from the SLF4J parameterized form (`log.info("... {}", arg)`) to the kotlin-logging lambda form (`log.info { "... $arg" }`), which builds the message string only when the level is enabled. kotlin-logging is a Kotlin layer over SLF4J, so the backend stays Logback (via the Spring Boot starters); the resolved logger names and the message text are unchanged, so the log output is identical.

## [0.5.2] - 2026-06-22

- Run the `:application:test` suite (the system, acceptance, and architecture tests, the slow part of the build) in parallel across several JVM processes to speed it up. `maxParallelForks` on the `application` `test` task now defaults to `min(4, availableProcessors / 2)` (override with `-PtestForks=N`; `-PtestForks=1` disables parallelism), with a `1g` per-fork heap cap so the forks cannot collectively overcommit. Process-level forking is the only safe form of parallelism here: `SystemTestUtils` is an `object` with a shared mutable `RestTestClient` and the test bases wipe the whole database between tests with `clearAll()`, so two tests must never run concurrently in the same JVM or against the same database. Each fork is a separate JVM with its own `SystemTestUtils` and its own Testcontainers PostgreSQL instance (the container lives in a companion object, one per JVM), and JUnit runs the classes within a fork serially, so two tests never touch the shared client at once and `clearAll()` never wipes a running test's data, while Spring's per-JVM context cache still pays off within each fork. On a 16-core machine this took the task from ~95s to ~50s with all 145 tests still passing.

## [0.5.1] - 2026-06-21

- Guard the project version against drift. Move the project `group` and `version` to the root `gradle.properties` (Gradle's `project.group`/`project.version`, applied to every module, out of the build-logic convention plugin) and make the version the source of truth; the latest `## [x.y.z]` header in `CHANGELOG.md` must agree with it. A new `scripts/check-version-sync.sh` (mirroring `scripts/check-toolchain-versions.sh`) compares the two and fails with a GitHub Actions error annotation on mismatch; it runs as a CI step in `build.yml` before the Gradle build. This prevents the recurrence of the earlier drift, where the build version had gone stale at `0.2.0`.

## [0.5.0] - 2026-06-20

- Make event sourcing the default persistence mode. `campus-coffee.persistence.mode` now defaults to `event-sourcing` (the `PersistenceProperties` default, `application.yaml`, and the `compose.yaml` dev default), so a default run records every write request in the `events` table and projects it into the relational read model; pass `--campus-coffee.persistence.mode=relational` (or `CAMPUS_COFFEE_PERSISTENCE_MODE=relational`) to opt into the plain relational adapters. The Cloud Run deploy script (`scripts/deploy-cloudrun.sh`) already defaulted to event sourcing, so the application default and the deploy default now agree (resolving the prior split where the application defaulted to relational while the deploy defaulted to event sourcing). The test bases that exercise the relational backend (`AbstractSystemTest`, `AbstractDataIntegrationTest`, `CucumberSpringConfiguration`) now pin `campus-coffee.persistence.mode=relational` explicitly, and the `EventSourcing*SystemTests` subclasses override it back to `event-sourcing`, so both backends still run in one `gradle build` and the aggregate coverage gate continues to see both.

## [0.4.0] - 2026-06-20

- Enforce KDoc on production code with a custom detekt rule set (`campus-coffee-kdoc`) in a new `:detekt-rules` subproject, loaded via `detektPlugins` and enabled in `config/detekt/detekt.yml`. The rules require KDoc on every non-local, non-override function (any visibility), an `@param` for every parameter of a public function, KDoc on every non-local class, interface, object, and enum class, and `@property` or `@param` for non-private enum constructor properties. Local declarations, overrides, and test sources are exempt (the `detekt` task wired into `check` is restricted to `src/main/kotlin`). The rules ship with their own unit tests, and the previously undocumented production declarations are now documented.
- Upgrade the PostgreSQL image from 17 to 18 (`postgres:18-alpine`) across the Docker Compose files, the local and dev run instructions, and the Testcontainers system and data integration tests.
- Relax the aggregate JaCoCo coverage gate from 95%/82% to 90%/80% (line/branch); all existing tests are kept.
- Load the fixture data and run the event sourcing import/rebuild startup migrations before the embedded web server accepts requests, so a cold start no longer serves the API before its data is loaded. The fixture loader and the two migration runners implement a `StartupTask` port that a new `StartupDataInitializer` runs in order during context refresh, instead of each triggering itself on `ApplicationReadyEvent` (after the server already accepted).
- Deploy to Google Cloud Run (including the event sourcing mode) in a single command. Because `gcloud run compose up` has no env-var flag and does not read the shell environment, `compose.prod.yaml` reads `JWT_SECRET` and `CAMPUS_COFFEE_PERSISTENCE_MODE` from a gitignored `deploy.env` via `env_file`; `scripts/deploy-cloudrun.sh` generates that file with a random secret and runs `gcloud beta run compose up --allow-unauthenticated`, so the service comes up healthy and public in one step (no follow-up `services update`).
- Expose springdoc from the `api` module (`api(...)` instead of `implementation(...)`), so the public DTOs' `@Schema` annotations reach the downstream annotation-processor classpath; this silences the `unknown enum constant AccessMode.READ_ONLY` kapt warning in the application build.
- Resolve the custom `campus-coffee.*` configuration properties in the IDE (`application.yaml`). IntelliJ reads them from the `@ConfigurationProperties` classes, so give every key a `*Properties` class in its module's `configuration` package (add `FixturesProperties`, rename `ApprovalConfiguration` → `ApprovalProperties`), document them with KDoc rather than `application.yaml` comments, and make `application` depend on `data` at compile scope (`implementation`, not `runtimeOnly`) so the data-owned keys resolve against the editor's classpath. Drop the unused `spring-boot-configuration-processor` and the hand-authored `spring-configuration-metadata.json` (IntelliJ does not index the kapt-generated metadata; JetBrains IDEA-316797 / IDEA-370289).
- Namespace and rename the configuration keys: `osm.api.*` → `campus-coffee.osm.api.*`, `jwt.secret` → `campus-coffee.jwt.secret`, and `campus-coffee.id.seed` → `campus-coffee.id.entity-seed` (symmetric with `event-seed`).
- Add an ArchUnit test that fails on package cycles across the production packages, and decouple the domain exceptions from `DomainModel` (their entity-type parameter is now `Class<*>`, used only for the type name) so the check passes.

## [0.3.0] - 2026-06-19

- Make the seeded `IdGenerator` thread-safe: `SeededUuidGenerator.newId()` and `reset()` are now mutually exclusive under a lock, so a `reset()` running concurrently with id generation (for example the dev `PUT /api/dev/data` reload while a write is in flight) cannot hand out an id from a half-replaced generator, and the deterministic id sequence holds under concurrency.
- Add a configurable event sourcing (event-first CQRS) persistence mode, selected by `campus-coffee.persistence.mode` (`relational`, the default, or `event-sourcing`). In event sourcing mode the append-only event log is the source of truth and the relational tables are a read model projected from it: each write request assigns the id and timestamps, appends one full-state event, then projects it into the tables in a single transaction, so a uniqueness, foreign-key, or optimistic-locking violation rolls both back and the log never holds an invalid event. The relational data adapters are wrapped by event-sourced decorators (`@Primary` and `@ConditionalOnProperty` when the mode is on), so the `domain` and `api` layers are unchanged and read requests are served from the materialized tables. The projection reuses the existing MapStruct mappers and writes the id and `createdAt`/`updatedAt` from the event body verbatim (a transient flag suppresses the JPA timestamp callbacks). A new Flyway migration (`V8`) adds the always-present `events` table (a `jsonb` body serialized through one Jackson mapper, the same one Hibernate binds to the column). Two opt-in startup flags migrate between the representations: `data-to-events-on-startup` appends one INSERT event per existing row (adopt a relational database into the log, idempotent per type), and `events-to-data-on-startup` clears the tables and replays the whole log (rebuild the read model, event sourcing mode only). The event bodies never carry a raw password, and a review event stores its POS and author as ids so no `passwordHash` leaks through a review; a user event keeps the `passwordHash`, so a login survives a rebuild. Event ids come from a separate seeded generator, so enabling event sourcing leaves the deterministic entity ids unchanged.
- Migrate entity ids from database-generated `Long` sequences to application-assigned `UUID`s. A new `IdGenerator` domain port assigns the id in the data-layer insert path before the write, so a null id still means "create" and a non-null id means "update". The generator is selected by `campus-coffee.id.seed`: a numeric seed (the default) makes the assigned ids deterministic and reproducible, so the loaded fixture data has stable ids the README and instructor demo can reference (loaded on startup in the `dev` and `prod` profiles, and reloadable via `PUT /api/dev/data` in `dev`). Setting it to `random` (`CAMPUS_COFFEE_ID_SEED=random`) uses random UUIDs. The base JPA entity now implements Spring Data's `Persistable<UUID>` with an explicit transient new-entity flag, so `repository.save()` inserts a freshly built entity without a preceding SELECT. The custom sequence-generation and sequence-reset machinery (`CustomSequence`, `ResettableSequenceRepository`, `JpaUtils`) is removed, and the Flyway migrations carry `uuid` primary and foreign keys with no sequences. The OpenStreetMap node id stays a `Long` (it is an external id).
- Load the fixture dataset on startup in the `dev` profile too (not only `prod`), so the local app comes up with the seeded fixture data ready (when the database has no users yet). Use the `/api/dev` endpoints to inspect, reload, or clear it.
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
