# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CampusCoffee is a Spring Boot application for managing Points of Sale (POS) like cafés and coffee shops on campus. It follows a **hexagonal (ports-and-adapters) architecture** with strict layer separation enforced by ArchUnit tests.

## Architecture

The project uses a **multi-module Gradle structure** (Kotlin DSL) with four modules:

### Module Dependencies
- **domain**: Core business logic, domain models, and port interfaces (no external dependencies except validation).
- **api**: REST API layer with controllers, DTOs, and DTO mappers (depends on: domain).
- **data**: Data layer with JPA entities, repositories, and the OpenStreetMap HTTP client (depends on: domain).
- **application**: Main Spring Boot application that wires everything together (depends on: domain, api, data at runtime).

### Layer Rules (Enforced by ArchUnit)
From `application/src/test/kotlin/de/seuhd/campuscoffee/tests/architecture/ArchitectureTests.kt`:

- **api** layer may only be accessed by **application**.
- **domain** layer may only be accessed by **api**, **data**, and **application**.
- **data** layer may only be accessed by **application**.
- **application** layer may not be accessed by any layer.

### Ports and Adapters Pattern

The domain defines **port interfaces** that adapters implement:

- **API Ports** (`domain/src/main/kotlin/de/seuhd/campuscoffee/domain/ports/api/`): Generic service interface `CrudService<DOMAIN, ID>` and concrete service interfaces such as `PosService`, `UserService`, and `ReviewService`.
- **Data Ports** (`domain/src/main/kotlin/de/seuhd/campuscoffee/domain/ports/data/`): Generic data service interface `CrudDataService<DOMAIN, ID>` and concrete service interfaces such as `PosDataService`, `UserDataService`, `ReviewDataService`, and `OsmDataService`.

Service **implementations**:
- API services in `domain/src/main/kotlin/de/seuhd/campuscoffee/domain/implementation/`.
- Data services in `data/src/main/kotlin/de/seuhd/campuscoffee/data/implementations/`.

### Two Interchangeable Data Adapters (Persistence Mode)

The data ports have **two adapters**, selected by `campus-coffee.persistence.mode`:

- **`relational`**: the plain `@Service` implementations write straight to the tables.
- **`event-sourcing`** (default): event-sourced **Decorators** (the design pattern) in
  `data/src/main/kotlin/de/seuhd/campuscoffee/data/persistence/eventsourcing/` wrap the relational impls
  (`: PosDataService by delegate`, so the read and query methods auto-delegate). Both the decorator and the
  relational impl are adapters for the same domain port. They are
  `@Primary @ConditionalOnProperty(... "event-sourcing")`, so the domain binds to them only when the mode
  is on. An append-only **event log** (`events` table) is the source of truth and the relational tables are
  a **read model** projected from it: each write request appends one full-state event (`EventStore`) and
  projects it into the tables (`ReadModelProjector`) in one transaction, so a constraint violation rolls
  both back and the log never holds an invalid event. The projection reuses the MapStruct mappers and
  preserves the id and timestamps from the event body. The `domain` and `api` layers are unchanged; read
  requests are served from the materialized tables (no replay on read).

### Generic Base Classes

The codebase uses extensive generics to reduce duplication:

- **CrudController** (`api/src/main/kotlin/de/seuhd/campuscoffee/api/controller/CrudController.kt`): Generic REST controller for CRUD operations.
- **CrudService** / **CrudServiceImpl**: Generic CRUD service interface and implementation.
- **CrudDataService** / **CrudDataServiceImpl**: Generic data service interface and implementation.
- **DtoMapper** / **EntityMapper**: Generic mapping interfaces using MapStruct.

Domain-specific controllers/services extend these base classes (e.g., `PosController extends CrudController<Pos, PosDto, UUID>`; the domain type comes first).

## Build and Run Commands

### Prerequisites
- Docker daemon must be running to use a database in the `dev` profile or to run the tests that use *Testcontainers*.
- Java 25 and Gradle 9.5, provisioned via `mise.toml` (no Gradle wrapper). Run Gradle through mise
  (CI uses `jdx/mise-action`). The build pins a **Java 25 toolchain with no auto-download**, so a
  JDK 25 must be present on the machine — mise supplies it; without it the build fails with "no
  matching toolchains".
- The Java major version has a **single source of truth**: the `java` entry in
  `gradle/libs.versions.toml`. The convention plugins resolve it for the Gradle toolchain
  (`java-conventions`) and the Kotlin `jvmTarget` (`kotlin-conventions`); `mise.toml` and the
  Dockerfile runtime image pin the same major by hand. `scripts/check-toolchain-versions.sh` (a CI
  step in `build.yml`) fails the build if they drift. To bump the JDK, change the catalog entry and
  the two hand-written pins together. The Docker **build** stage no longer pins a JDK/Gradle version:
  it installs mise and provisions both from `mise.toml`, mirroring CI.
- The project version has a **single source of truth**: the `version` property in the root
  `gradle.properties` (Gradle sets it on `project.version` for every module). The latest `## [x.y.z]` header
  in `CHANGELOG.md` must match it; `scripts/check-version-sync.sh` (a CI step in `build.yml`) fails the
  build if they drift. When cutting a release, bump that property and add the `CHANGELOG.md` entry together.

### Build

```shell
gradle build
```

### Format, Lint, and Static Analysis (ktlint + detekt)

The Kotlin sources are formatted and linted with ktlint (official Kotlin style, configured via the root
`.editorconfig`). `gradle build` fails on violations because `ktlintCheck` is wired into `check`; apply
the fixes with:

```shell
gradle ktlintFormat
```

Static analysis runs via detekt (`dev.detekt`, pinned at `2.0.0-alpha.5`; detekt 2.0 alphas require the exact
Kotlin version they were built against, which is why Kotlin is pinned at 2.4.0). It is applied by the `kotlin-conventions`
plugin and wired into `check`, so `gradle build` and CI fail on findings. A per-module
`detekt-baseline.xml` grandfathers pre-existing findings; regenerate it with `gradle detektBaseline`.

On top of detekt's defaults the build enforces a **custom KDoc rule set** (`campus-coffee-kdoc`),
authored in the `:detekt-rules` tooling subproject and loaded via `detektPlugins`. It requires:
every non-local, non-override function to have KDoc (any visibility); public functions to document
every parameter with `@param`; every non-local class, interface, object, and enum class to have KDoc;
and non-private enum constructor properties to be documented with `@property` or `@param`. Local
declarations, overrides, and **test sources** are exempt (the `detekt` task wired into `check` is
restricted to `src/main/kotlin` in `kotlin-conventions`). The rules are enabled in `config/detekt/detekt.yml` and
covered by their own unit tests (`gradle :detekt-rules:test`). To add or change a rule, edit
`detekt-rules/src/main/kotlin/de/seuhd/campuscoffee/detekt/` and its `META-INF/services` provider.

### Start PostgreSQL Database

```shell
docker run -d --name db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18-alpine
```

### Run Application (dev profile)

```shell
gradle :application:bootRun --args='--spring.profiles.active=dev'
```

The `dev` profile:
- Enables Swagger UI at `http://localhost:8080/api/swagger-ui.html`.
- Enables API docs at `http://localhost:8080/api/api-docs`.
- Loads the fixture dataset on startup (`campus-coffee.fixtures.load-on-startup: true`, when the database
  has no users yet), so the app comes up with the seeded ids ready.
- Registers the dev-only `DevController` (in the `api` layer) under `/api/dev`:
  `GET /api/dev/data` reports the counts, `PUT /api/dev/data` replaces the data with the fixture
  dataset (clear + seed; idempotent, reassigning the same seeded ids), and `DELETE /api/dev/data` clears it.

The fixture load on startup happens in the `dev` and `prod` profiles (both set
`campus-coffee.fixtures.load-on-startup`); the database persists across application restarts, and the
loader skips when users already exist.

### Run in relational mode (event sourcing is the default)

Event sourcing is the default persistence mode, so a normal `dev` run already uses the event-first
adapters (the event log is the source of truth and the tables a read model projected from it). Every write
is recorded in the `events` table (`SELECT change_type, entity_type FROM events ORDER BY seq`). To migrate
an existing relational database into the log, restart once with
`--campus-coffee.persistence.data-to-events-on-startup=true` (appends one INSERT event per row), then
rebuild the tables from the log with `--campus-coffee.persistence.events-to-data-on-startup=true`.

To run with the plain relational adapters instead (write straight to the tables, no event log):

```shell
gradle :application:bootRun --args='--spring.profiles.active=dev --campus-coffee.persistence.mode=relational'
```

Behavior is identical.

### Run Tests

All tests:

```shell
gradle test
```

The `:application:test` task (the system, acceptance, and architecture tests, the slow part of the build)
runs in parallel across several JVM processes; `maxParallelForks` defaults to `min(4, cpu / 2)`. This is
safe because each fork is a separate JVM with its own `SystemTestUtils` (an `object` with a shared mutable
`RestTestClient`) and its own Testcontainers PostgreSQL instance, and JUnit runs the classes within a fork
serially, so two tests never touch the shared client at once and `clearAll()` never wipes a running test's
data (in-JVM parallelism would race on both). Each fork boots a full Spring context and a database
container, so on a machine with little memory override the count with `-PtestForks=N`; `-PtestForks=1`
disables parallelism.

Single test class (scope the task to the module that contains the test; the bare `test` task runs in
every module, and the `--tests` filter fails the modules that have no matching test):

```shell
gradle :domain:test --tests "PosServiceTest"
```

Single test method (test methods use backtick sentence names; quote the filter):

```shell
gradle :domain:test --tests "PosServiceTest.getById returns the POS from the data service"
```

### Code Coverage and Mutation Testing

- **Coverage (JaCoCo)**: the `coverage` subproject (the `jacoco-report-aggregation` plugin) aggregates
  execution data from all modules into one report at
  `coverage/build/reports/jacoco/testCodeCoverageReport/`. Aggregation is required because
  `domain`/`api`/`data` are largely covered by the `application` system and acceptance tests, not by
  their own tests. `gradle build` (or `gradle check`) builds the report and enforces the gate: the
  `coverageGate` task (a `JacocoCoverageVerification` in `coverage/build.gradle.kts`, wired into `check`)
  fails the build when aggregated line or branch coverage is below its minimums (90% line, 80% branch).
  The minimums track current coverage; raise them when adding tests, never lower them to make a build pass.
- **Mutation testing (PITest)**: opt-in and local via the `-Pmutation` property and the per-module
  `pitest` task (e.g., `gradle :domain:pitest -Pmutation`). Each module runs PIT against its own tests and
  writes its own report under `<module>/build/reports/pitest/index.html`: `domain` mutates `domain.*`,
  `api` mutates `api.*`, and `data` mutates `data.*`. The `application` cross-module run
  (`gradle :application:pitest -Pmutation`) additionally mutates `api.*`/`data.*` against the system and
  acceptance tests, as the Maven build did; it adds the `api`/`data` `classes/kotlin/main` directories as
  `additionalMutableCodePaths`. The generated `*MapperImpl` classes are excluded from mutation, mirroring the JaCoCo gate.
  Per-module `targetClasses` live in each module's `build.gradle.kts`; shared config is in the
  `de.seuhd.campuscoffee.pitest-conventions` convention plugin. Select the mutator group with
  `-Ppitest.mutators=DEFAULTS|STRONGER|ALL`.
- When adding a feature, also add tests; use surviving mutants to find missing assertions. The
  handwritten mapping logic in `PosEntityMapper` (house-number parsing), `ReviewDtoMapper` (expression
  mappings), and `HouseNumberConverter` contains real logic and is kept in scope for both tools.

### Docker

Build image:

```shell
docker build -t campus-coffee:latest .
```

Run with Docker Compose (the Compose file defaults `DB_HOST` to `localhost` for Cloud Run, so set
`DB_HOST=db` locally):

```shell
docker compose down && DB_HOST=db docker compose up
```

### Dependency Updates

Dependencies and tools are kept current automatically:
- **Dependabot** (`.github/dependabot.yml`) opens weekly PRs for the GitHub Actions and the Gradle
  dependencies and plugins (resolved from the `libs.versions.toml` catalog), grouping minor and patch
  bumps and keeping majors separate.
- A weekly **`mise-outdated`** workflow runs `mise outdated` and opens or updates an issue when the
  mise-managed tools (the JDK, Gradle, gcloud, and python in `mise.toml`) fall behind. Dependabot has no
  mise ecosystem, so bump those pins by hand.

## Database

- **Database**: PostgreSQL 18.
- **Migrations**: Flyway (located in `data/src/main/resources/db/migration/`).
- **ORM**: JPA with Spring Data.
- **Connection**: Configured in `application/src/main/resources/application.yaml`.

Migration files follow Flyway naming convention (e.g., `V1__create_pos_table.sql`, `V2__create_users_table.sql`).

`V8__create_events_table.sql` adds the `events` table for the event sourcing mode. It always runs and the
`EventEntity` is always mapped (the table exists in both modes); in relational mode nothing writes to it.
The table is append-only: an application-assigned `uuid` id, a database-assigned monotonic `seq` (the
replay order, since the UUID id is not monotonic), `change_type`, `entity_type`, `entity_version`, a `jsonb`
`body`, and `created_at`.

## Testing Strategy

- **Unit and Integration Tests**: In `domain/src/test/kotlin/` (e.g., `PosServiceTest`, `ReviewServiceTest`)
- **System Tests**: In `application/src/test/kotlin/de/seuhd/campuscoffee/tests/system/` (e.g., `PosSystemTests`, `UsersSystemTests`)
  - Use Testcontainers for PostgreSQL.
  - Use Spring's `RestTestClient` for API testing.
  - Extend `AbstractSystemTest` base class.
- **Acceptance Tests**: In `application/src/test/kotlin/de/seuhd/campuscoffee/tests/acceptance/`
  - Cucumber BDD tests with `.feature` files in `application/src/test/resources/de/seuhd/campuscoffee/tests/acceptance/`
  - Step definitions in `CucumberPosSteps.kt` and `CucumberReviewSteps.kt`
- **Architecture Tests**: In `application/src/test/kotlin/de/seuhd/campuscoffee/tests/architecture/`
  - ArchUnit tests enforce hexagonal architecture rules
- **Both persistence modes** run in one `gradle build`, so the aggregate coverage gate sees both. System
  tests are persistence-agnostic, so the same suites run on both backends. The application default is event
  sourcing, but the test bases (`AbstractSystemTest`, `AbstractDataIntegrationTest`, and
  `CucumberSpringConfiguration`) pin `campus-coffee.persistence.mode=relational` so they exercise the
  relational backend regardless of the runtime default; the thin subclasses in `EventSourcingSystemTests.kt`
  (e.g. `EventSourcingPosSystemTests : PosSystemTests()`) override that with
  `campus-coffee.persistence.mode=event-sourcing`, which forks a separate Spring context (an inline
  `@TestPropertySource` on a subclass overrides the base's for the same key). The event-sourcing-specific
  behavior (event writing, rollback, replay, import/rebuild runners, per-mode bean selection) is covered by
  the data-layer integration and wiring tests under `data/.../persistence/eventsourcing/`, which extend
  `AbstractEventSourcingDataIntegrationTest`.

### Test Naming

Test methods (those annotated with `@Test` or `@ParameterizedTest`) use Kotlin backtick names that
read as a sentence describing the behavior under test. The structure is the same throughout: active
voice, present tense, the subject under test first (a scenario for behavior tests, the function name
for focused unit tests), then the **outcome stated as the fact the test actually asserts** — the
explicit HTTP status for system tests (`409 Conflict`, `404 Not Found`), the exception type, or the
returned value. Avoid `should` and vague status nouns (`returns conflict`). Examples:

- ``fun `creating a POS with a duplicate name returns 409 Conflict`()`` (system test)
- ``fun `upsert throws DuplicationException for a duplicate POS name`()`` (data test)
- ``fun `findByName returns the matching POS and null when none matches`()`` (repository test)

ktlint's `function-naming` rule permits these for test-annotated functions. Non-test functions (setup
methods like `@BeforeEach`/`@AfterAll`, `@MethodSource` providers, Cucumber step definitions, and
private helpers) keep conventional camelCase names.

## Key Technologies

- **Spring Boot 4.0.6** (Spring Framework 7).
- **Kotlin** on JDK 25; nullability is expressed with Kotlin's nullable types.
- **MapStruct** for object mapping (DTOs <-> domain models <-> entities), run via kapt.
- **ktlint** for Kotlin formatting and linting (the official Kotlin style; `ktlintCheck` runs as part of `check`).
- **detekt** for Kotlin static analysis (`dev.detekt` `2.0.0-alpha.5`, gated via `check`; a per-module baseline grandfathers existing findings), plus a custom `campus-coffee-kdoc` rule set in `:detekt-rules` enforcing KDoc on production code.
- **Bean Validation** (Jakarta Validation) for input validation (validation happens in the controllers based on the DTOs, before mapping them to domain models).
- **OpenAPI/Swagger** (SpringDoc) for API documentation.
- **Spring `@HttpExchange`** declarative HTTP client over `RestClient` (OpenStreetMap API integration).
- **Testcontainers** for system tests.
- **Cucumber** for BDD acceptance tests.
- **ArchUnit** for architecture testing.
- **kotlin-logging** (`io.github.oshai:kotlin-logging-jvm`) for logging: a Kotlin layer over SLF4J. Loggers are declared as `private val log = KotlinLogging.logger {}` in a companion object, and log calls use the lambda form (`log.info { "..." }`), which builds the message only when the level is enabled. The backend is Logback via the Spring Boot starters.

## Important Patterns

### Error Handling

Domain exceptions in `domain/src/main/kotlin/de/seuhd/campuscoffee/domain/exceptions/`:
- `NotFoundException`: Entity not found (404).
- `DuplicationException`: Duplicate unique fields (409).
- `ValidationException`: Business rule violation (400).
- `MissingFieldException`: Required field missing (400).
- `ConcurrentUpdateException`: Optimistic-locking conflict (409).
- `DeletionConflictException`: Deletion blocked because other data references the entity (409).
- `ExternalServiceException`: An external service (e.g., OpenStreetMap) failed or was unreachable (502).

Global exception handler: `api/src/main/kotlin/de/seuhd/campuscoffee/api/exceptions/GlobalExceptionHandler.kt`.
It extends `ResponseEntityExceptionHandler`, so the standard Spring MVC exceptions also map to their
proper status codes (an unmapped path returns 404, a wrong HTTP method 405) instead of a generic 500.

The REST API is JSON-only: `ApiWebConfig` removes the XML message converter, so a client's `Accept`
header cannot switch responses to XML (the OSM client parses XML with its own `XmlMapper`).

### MapStruct Configuration

MapStruct runs as a Kotlin annotation processor via kapt, applied through the `de.seuhd.campuscoffee.kotlin-kapt-conventions` convention plugin (`build-logic/`); the `api` and `data` modules declare `kapt(mapstruct-processor)`. The generated `*MapperImpl` classes are excluded from the coverage and mutation gates.

### IDE Configuration Metadata

The custom `campus-coffee.*` keys resolve in IntelliJ's `application.yaml` editor because the IDE reads the
`@ConfigurationProperties` classes directly from source. Two rules keep that working:

- **Every custom key has a `@ConfigurationProperties` class**, named `*Properties` and living in its module's
  `configuration` package: `ApprovalProperties` (`domain`), `IdProperties` / `PersistenceProperties` /
  `OsmApiProperties` (`data`), and `JwtProperties` / `FixturesProperties` (`application`). Document each
  property with **KDoc on the class** (the IDE shows it as the key's quick-doc), never with comments in
  `application.yaml`. To add a key, add it to the relevant class.
- **The data module is a compile dependency of `application`** (`implementation(project(":data"))`, not
  `runtimeOnly`). The IDE resolves `application.yaml` against the `application` module's compile classpath, so
  a `runtimeOnly` data dependency would leave the data-owned keys (`campus-coffee.id.*`,
  `campus-coffee.persistence.*`, `campus-coffee.osm.api.*`) flagged as unresolved while editing the file.

We deliberately do **not** use the `spring-boot-configuration-processor` or any hand-authored
`spring-configuration-metadata.json`. That processor runs through kapt, which writes the generated metadata
under `build/tmp/kapt3` — a directory IntelliJ does not index — so it never helped the IDE (JetBrains
IDEA-316797 / IDEA-370289; kapt is also in maintenance mode). kapt is applied only to `api` and `data`, for
MapStruct.

### Identifier Generation

Entity ids are application-assigned `UUID`s. The domain defines an `IdGenerator` port
(`domain/.../ports/IdGenerator.kt`); the data-layer `IdGeneratorConfiguration` selects the adapter from the
`campus-coffee.id.entity-seed` property. A numeric seed (the default) yields a `SeededUuidGenerator` whose
deterministic sequence makes the loaded fixture ids reproducible across runs, so the README and instructor
examples can reference them. The fixtures load on startup in the dev and prod profiles (both set
`campus-coffee.fixtures.load-on-startup`), and the dev `PUT /api/dev/data` reloads them on demand. The dev
reload resets the generator first, so repeated loads reassign the same ids. The tests use the same seeded
generator.
Setting the seed to `random` (e.g., `CAMPUS_COFFEE_ID_ENTITY_SEED=random`) yields
`UUID.randomUUID()` for a deployment that wants random ids. The id is assigned in the data-layer insert
path (`CrudDataServiceImpl`, `ReviewApprovalDataServiceImpl`), so a null `domain.id` still means "create" and
a non-null id means "update". The base `Entity` (`@MappedSuperclass`) implements Spring Data's
`Persistable<UUID>` with an explicit transient new-entity flag (flipped in `@PostLoad`/`@PostPersist`), so
`repository.save()` issues an INSERT for a freshly built entity with no preceding SELECT. There are no
database sequences.

### OpenAPI Customization

Custom OpenAPI annotations in `api/src/main/kotlin/de/seuhd/campuscoffee/api/openapi/`:
- `@CrudOperation` for common CRUD operations.
- `CrudOperationCustomizer` for customizing OpenAPI spec.
- Reduces repetitive annotations in controllers.

## Configuration

- Main config: `application/src/main/resources/application.yaml`.
- Dev profile activates on `spring.config.activate.on-profile: dev`.
- Custom properties:
  - `campus-coffee.osm.api.base-url`: OpenStreetMap API endpoint.
  - `campus-coffee.osm.api.connect-timeout` / `campus-coffee.osm.api.read-timeout`: HTTP timeouts of the OSM client (defaults: 5s/10s).
  - `campus-coffee.approval.min-count`: Minimum number of approvals needed for reviews to be approved.
    Required and must be >= 1; binding fails at startup otherwise.
  - `campus-coffee.id.entity-seed`: Seed for the application-assigned entity UUIDs. A number (the default) makes
    the assigned ids deterministic and reproducible (so the loaded fixture data has stable ids); `random`
    (e.g., `CAMPUS_COFFEE_ID_ENTITY_SEED=random`) uses random UUIDs instead. A separate generator with its own
    seed (`campus-coffee.id.event-seed`, default `100`) assigns the event log's ids, so enabling event
    sourcing leaves the entity ids unchanged.
  - `campus-coffee.persistence.mode`: `event-sourcing` (the default) or `relational`. Selects the data
    adapter (see Ports & Adapters). An unknown mode value fails binding at startup.
  - `campus-coffee.persistence.data-to-events-on-startup`: when `true`, seed the event log from the existing
    rows on startup (import a relational database into the log; appends one INSERT event per row,
    idempotent per type). Off by default.
  - `campus-coffee.persistence.events-to-data-on-startup`: when `true`, rebuild the relational tables from
    the event log on startup (clear the tables and replay the whole log). Acts only in event sourcing mode;
    logs and skips in relational mode. Off by default.
  - `campus-coffee.jwt.secret`: HMAC signing secret for the stateless JWT bearer tokens. Required and at
    least 32 bytes; binding fails at startup otherwise. Supplied via `JWT_SECRET` (the dev profile has an
    insecure fallback, the prod profile none).
  - `campus-coffee.fixtures.load-on-startup`: when `true` and the database has no users yet, load the
    fixture dataset on startup (enabled in the dev and prod profiles).

The design is honest about its claims: in event sourcing mode the events are the source of truth and the
tables are a materialized read model rebuilt from the log. The `events` table retains `passwordHash` in a
user event (the same sensitivity as the `users` table, so a login survives a rebuild) but never the raw
`password`, and a review event stores its POS and author as ids, so no hash leaks through a review.

Deleting a review appends one `Review` DELETE event; the dependent `review_approvals` rows are removed by
the database's `ON DELETE CASCADE`, not by per-approval DELETE events. This stays consistent on a rebuild
because the replay applies the `Review` DELETE in append order (after the approval was inserted), so the
projection cascades the approval away again. The fixture load and the two startup migration runners
(import, rebuild) each implement the `StartupTask` domain port and run before the embedded web server
accepts requests, not on `ApplicationReadyEvent`: the `StartupDataInitializer` (a
`SmartInitializingSingleton`) runs every registered `StartupTask` in `order` during context refresh, so the
API is never served before its data is loaded. The events-to-data rebuild refuses to run against an empty
log, so it can never clear a populated read model it has nothing to replay.

## REST API Endpoints

Base URL: `http://localhost:8080/api`.

### POS Endpoints

- `GET /pos` - Get all POS.
- `GET /pos/{id}` - Get POS by ID.
- `GET /pos/filter?name={name}` - Filter by name.
- `POST /pos` - Create POS.
- `POST /pos/import/osm/{nodeId}?campus_type={type}` - Import from OpenStreetMap.
- `PUT /pos/{id}` - Update POS.
- `DELETE /pos/{id}` - Delete POS.

### User Endpoints

- `GET /users` - Get all users.
- `GET /users/{id}` - Get user by ID.
- `GET /users/filter?login_name={name}` - Filter by login name.
- `POST /users` - Create user.
- `PUT /users/{id}` - Update user.
- `DELETE /users/{id}` - Delete user.

### Review Endpoints

- `GET /reviews` - Get all reviews.
- `GET /reviews/{id}` - Get review by ID.
- `GET /reviews/filter?pos_id={id}&approved={true/false}` - Filter reviews.
- `POST /reviews` - Create review.
- `PUT /reviews/{id}/approve?user_id={id}` - Approve review (the approving user must differ from the author).

Notes on semantics:
- `POST` rejects a request body that carries an `id` (400); the server assigns ids.
- `PUT /reviews/{id}` may change the review text (the author or a moderator may edit it); a review's POS is fixed at creation (re-pointing it returns 400) and its author is fixed
  (an update keeps the original author), and the approval state (`approvalCount`/`approved`) is owned by the
  approval workflow, so an update keeps it.
- Creating a second review for the same POS by the same author returns 409 (`DuplicationException`,
  backed by the `uq_reviews_pos_author` database constraint, which also closes the concurrent-create race).
- `DELETE /pos/{id}` and `DELETE /users/{id}` return 409 when reviews still reference the entity.

## Working with the Codebase

### Adding a New Entity

1. Create domain model in `domain/src/main/kotlin/de/seuhd/campuscoffee/domain/model/objects/`.
2. Create service interface in `domain/src/main/kotlin/de/seuhd/campuscoffee/domain/ports/api/` (extend `CrudService<DOMAIN, ID>`).
3. Create data service interface in `domain/src/main/kotlin/de/seuhd/campuscoffee/domain/ports/data/` (extend `CrudDataService<DOMAIN, ID>`).
4. Create service implementation in `domain/src/main/kotlin/de/seuhd/campuscoffee/domain/implementation/` (extend `CrudServiceImpl<DOMAIN, ID>`).
5. Create JPA entity in `data/src/main/kotlin/de/seuhd/campuscoffee/data/persistence/entities/`.
6. Create repository in `data/src/main/kotlin/de/seuhd/campuscoffee/data/persistence/repositories/` (extend `JpaRepository`).
7. Create entity mapper in `data/src/main/kotlin/de/seuhd/campuscoffee/data/mapper/` (extend `EntityMapper`).
8. Create data service implementation in `data/src/main/kotlin/de/seuhd/campuscoffee/data/implementations/` (extend `CrudDataServiceImpl<DOMAIN, ENTITY, REPOSITORY, ID>`).
9. Create DTO in `api/src/main/kotlin/de/seuhd/campuscoffee/api/dtos/` (extend `Dto<ID>`).
10. Create DTO mapper in `api/src/main/kotlin/de/seuhd/campuscoffee/api/mapper/` (extend `DtoMapper<DOMAIN, DTO>`).
11. Create controller in `api/src/main/kotlin/de/seuhd/campuscoffee/api/controller/` (extend `CrudController<DOMAIN, DTO, ID>`). Map paths relative to the resource (e.g., `@RequestMapping("/widgets")`); the `/api` base is applied centrally by `ApiWebConfig`.
12. Create Flyway migration in `data/src/main/resources/db/migration/`.

### Constraint Violations

Database uniqueness constraints are automatically converted to `DuplicationException` via `ConstraintMapping` in `data/src/main/kotlin/de/seuhd/campuscoffee/data/constraints/`. Register custom constraint mappings there.
