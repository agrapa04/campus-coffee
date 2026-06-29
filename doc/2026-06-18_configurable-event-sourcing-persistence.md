# Configurable event sourcing (event-first CQRS) persistence

Design as implemented (this document tracks the as-built feature). **Builds on** the id migration in
`doc/2026-06-18_migrate-ids-to-uuid.md` (ids are application-assigned `UUID`s with an `IdGenerator` bean).

## Context

Add a second, genuinely event-sourced data layer, selectable by a config flag, mirroring the
`ase24-taskboard` approach (whose event sourcing impl appended the event and a database trigger derived the
table). We use **event-first** writes: the event log is the source of truth and the relational tables are a
projection derived from it, so the "source of truth" framing is accurate rather than overclaimed. The
projection moves from taskboard's SQL triggers into testable Kotlin, reusing the existing MapStruct mappers
(the decoupling-via-mappers lesson). Reads stay fast — served from the materialized tables, no
replay-on-read. Default mode stays relational; the `domain` and `api` layers are unchanged.

## Design

- **Write = event-first, in one transaction.** The event-sourced `*DataService` is a decorator
  (`: PosDataService by delegate`, so reads/queries auto-delegate to the relational impl). Its mutating
  methods do NOT call `delegate.upsert`; they: assign the id (via the entity `IdGenerator`) and set
  `createdAt`/`updatedAt` (on create) so the event body is complete, **append the event**, then **project**
  the change into the relational table, all in one `@Transactional` method (the shared logic lives in
  `EventSourcedMutator`). The projector
  writes those exact id and timestamps, bypassing `@PrePersist`/`@PreUpdate` so they are not clobbered (the
  entity's transient `isNew` flag, not `createdAt`, drives persist-vs-update). The projection's table write
  enforces `uq_*`/FK/`@Version`; a violation rolls back the whole transaction, so the log never holds an
  invalid event. (Invariants are thus enforced by the projection's DB constraints — a pragmatic choice that
  keeps the event authoritative while reusing the DB's enforcement.)
- **`EventStore` + `EventEntity` + `EventRepository`** (package `data.persistence.eventsourcing`): one generic
  full-state event. `EventEntity`: `changeType: ChangeType {INSERT,UPDATE,DELETE}`, `entityType: String`,
  `entityVersion: Long` (a payload-schema-version constant, `1` for the current format), `body: Map<String,
  Any?>` (`@JdbcTypeCode(SqlTypes.JSON)`), `createdAt`, a database-assigned `seq` (the replay order, since
  the UUID id is not monotonic); own `id: UUID` from a dedicated event-id generator with its own seed
  (`campus-coffee.id.event-seed`), separate from the entity generator so the entity ids do not depend on
  whether event sourcing is on. The domain object's own id lives inside `body` (no separate id column; no
  `created_by`, since the data layer cannot see the actor). `entityType` is derived from
  `domain::class.simpleName` (and a `KClass` token for delete/clear, so there is one source). `EventStore`
  owns the JSON authority: a Jackson 3 `JsonMapper` pinned to the `jsonb` column via
  `hibernate.type.json_format_mapper` (a `Jackson3JsonFormatMapper`), so the `Map` and the column use the
  same mapper. A mixin drops the raw `User.password`, and a custom serializer flattens `Review`'s `Pos`/
  `User` to their ids (so no `passwordHash` leaks via review events; `User` events retain `passwordHash`,
  documented, so a login survives a rebuild).
- **`ReadModelProjector`** (data): applies one event to the tables — INSERT (build the entity via the
  mapper, set the id and `createdAt`/`updatedAt` from the event body, persist bypassing the `@PrePersist`
  timestamp callback so the row matches the body; the transient `isNew` flag makes it a `persist`), UPDATE
  (load + `updateEntity` + write the body's `updatedAt`), DELETE (`repository.delete`, FK violation →
  `DeletionConflictException`). Reused by the decorators' live writes **and** by the events-to-data replay,
  so the id/timestamp-preserving insert lives in one place.
- **Toggle:** `campus-coffee.persistence.mode = relational (default) | event-sourcing`. The relational
  impls stay plain `@Service`; each decorator is `@Service @Primary
  @ConditionalOnProperty(... "event-sourcing")`, injecting the concrete relational impl as `delegate` plus
  `EventSourcedMutator` (the shared helper that holds the `EventStore`, the `ReadModelProjector`, and the
  entity `IdGenerator`). A `PersistenceProperties @ConfigurationProperties("campus-coffee.persistence")`
  holds `mode`, `dataToEventsOnStartup`, `eventsToDataOnStartup` (an unknown mode fails startup, since it
  cannot bind to the enum); the runners read it.
- **events → data** (rebuild): a flag-gated (`events-to-data-on-startup`) data-component runs only in
  event sourcing mode and skips an empty log (so it never clears a populated read model with nothing to
  replay back). Otherwise it clears the tables and replays the whole log in append order
  (`findAllByOrderBySeqAsc`) through the `ReadModelProjector`, preserving ids + `createdAt`/`updatedAt`; the
  `@Version` counter restarts from zero, which has no effect. No `api` involvement.
- **data → events** (import an existing database): a flag-gated, `@Transactional` component that runs only
  in event sourcing mode; it reads the current rows and appends one INSERT event each in FK order (users/pos
  → reviews → approvals; **approvals via `ReviewApprovalRepository.findAll()`** since that port has no
  `getAll`); idempotent per type (skip when the type's log is non-empty).
- **Startup ordering — run before the web server accepts requests.** The import and rebuild runners and the
  application's `FixtureStartupLoader` each implement a `StartupTask` domain port (`val order`; `fun run()`).
  A `StartupDataInitializer` (a `SmartInitializingSingleton` in the application module) injects
  `List<StartupTask>` and runs them in ascending `order` during context refresh — import (0) → rebuild (100)
  → fixtures (200) — which, for a servlet app, is before `finishRefresh()` binds the Tomcat connectors. This
  replaces the original per-runner `@EventListener(ApplicationReadyEvent)` triggers, which fired after the
  connectors already accept and left a cold-start window where a request saw the empty tables. The
  coordinator depends only on the domain port (not the concrete `data` runners), so the application keeps its
  `runtimeOnly` dependency on `data`. Import runs before rebuild so a rebuild sees the imported events;
  fixtures run last so the load guard sees the rebuilt users and does not double-load.
- **Migration V8** creates the `events` table (`id uuid` PK, `seq bigint` identity for the replay order,
  `change_type`, `entity_type`, `entity_version`, `body jsonb`, `created_at`). Always runs (the entity is
  always mapped).
- **Reuses from the id migration:** the `IdGenerator` port (the event log gets its own instance with a
  separate seed) and the assigned-id `Persistable`/`isNew` handling, generalized into a `PersistableEntity`
  `@MappedSuperclass` shared by the read-model `Entity` and the `EventEntity`.

## Tests

All in one `gradle build`, so the aggregate coverage gate (95% line / 82% branch) sees both modes.

- The relational suites keep validating the shared read model.
- **`AbstractEventSourcingDataIntegrationTest`** (new `data` base, `@TestPropertySource mode=event-sourcing`)
  + data integration tests assert: an event is written per create/update/delete (correct
  `change_type`/`entity_type`/body and `entityVersion`); a duplicate still throws `DuplicationException` and
  **appends no event** (rollback); a `User`/`Review` event body contains **no** raw `password` and no leaked
  hash via reviews; `clear()` empties both the read tables and the log; `data → events` seeds one INSERT per
  existing row in FK order (idempotent); `events → data` reconstructs ids, business fields, and timestamps
  (compared to the pre-run `getAll`).
- **Startup-runner tests (for the thin branch margin):** boot a context with each flag on and drive the
  runner through its `StartupTask.run()` entry point so the gated bodies and branches execute —
  `data-to-events-on-startup=true` in ES mode (happy path) and run twice for the idempotent skip;
  `events-to-data-on-startup=true` in ES mode (rebuild) and once in **relational** mode for the log-and-skip
  branch; a both-flags-set boot asserts the import runner is ordered before the rebuild.
  `StartupDataInitializerTest` (a unit test) asserts the coordinator runs tasks in ascending order
  regardless of injection order and tolerates an empty list.
- **Startup-before-serving test:** `StartupDataInitializerIntegrationTest` boots the application with the
  fixture load and both migrations enabled and records the user count at `WebServerInitializedEvent` (which
  fires as the connectors bind); it asserts the fixtures are already present then, so it fails under the old
  `ApplicationReadyEvent` timing — a regression guard for the cold-start fix — and pins the fixtures-last
  order.
- **`PersistenceModeWiringTest`:** the injected `*DataService` beans are the relational impls in default
  mode and the `EventSourced*` decorators under `mode=event-sourcing`.
- **System tests run on both backends, not reimplemented.** System tests are persistence-agnostic, so the
  same suites must pass on either backend. The existing suites (`PosSystemTests`, `ReviewSystemTests`,
  `UsersSystemTests`) are `open`, and thin subclasses (`EventSourcingPosSystemTests : PosSystemTests()`,
  etc., in `EventSourcingSystemTests.kt`) re-run the same inherited tests under
  `@TestPropertySource(mode=event-sourcing)`, which forks a separate Spring context. `AbstractSystemTest`
  clears review approvals in both modes (via the `ReviewApprovalDataService` port), so the same base resets
  either backend.

ArchUnit: the new code is in the `data` layer and imports only `domain` + Spring/Jackson; no rule trips.

## Docs, changelog & version

- **`CHANGELOG.md`** (Keep a Changelog): add an `[Unreleased]` entry for the configurable
  event sourcing/CQRS persistence mode and, since this is a user-facing feature, open the next
  `## [x.y.z]` section (version is tracked only via CHANGELOG headings, latest `0.2.0`).
- **`CLAUDE.md`:** Ports & Adapters (the two interchangeable data adapters + the mode flag), Build/Run (the
  ES run line), Database (`V8` + the always-present `events` table), Testing (both-modes note), Configuration
  (the three `campus-coffee.persistence.*` properties). Note the design honestly: events = source of truth,
  the tables = a materialized read model rebuilt from the log; the events table retains `passwordHash` (same
  sensitivity as the `users` table) but never the raw `password`.
- **`README.md`:** add the ES run line (`--campus-coffee.persistence.mode=event-sourcing`), the
  data-to-events → events-to-data import/rebuild flow, and (under Deployment) deploying the ES mode to Google
  Cloud Run. **`INSTRUCTOR_EDA.md`:** a demo step that switches to ES mode and inspects the `events` table, plus
  the Cloud Run ES-deploy variant.
- **PITest:** no glob change; `de.seuhd.campuscoffee.data.*` already matches `data.persistence.eventsourcing.*`.

## Risks / verify first

- **`Map` ↔ `jsonb` round-trip with one `FormatMapper`.** Spring Boot 4 carries two Jackson majors; with
  `@JdbcTypeCode(SqlTypes.JSON)` Hibernate re-serializes the `Map` with whichever `FormatMapper` it picks.
  Resolved by binding the feature to Jackson 3 (Spring Framework 7's default) and pinning
  `hibernate.type.json_format_mapper` to the `EventStore`'s Jackson 3 `JsonMapper` (a
  `Jackson3JsonFormatMapper`), so the `Map` and the `jsonb` column use one mapper. A round-trip test
  (persist a body with `LocalDateTime` timestamps, read back, replay, assert they survive) guards it.
- **Transaction boundary.** The event append + the projection must share one transaction so a constraint
  violation rolls back both and the log never holds an invalid event; verify the append participates and
  `saveAndFlush` surfaces violations inside the method.
- **Branch-coverage margin is thin** (~0.82 today); the runner tests above must execute the skip/idempotent/
  both-flags branches or the gate fails. Re-run `gradle build` and confirm ≥ 0.82.
- **`events → data` fidelity:** ids preserved via the `Persistable` save; timestamps written from the body
  (bypass `@PrePersist`); `@Version` resets (harmless). Keep the test comparison consistent with that.

## Verification

`gradle build` green (both modes). Relational run unchanged. ES run
(`--campus-coffee.persistence.mode=event-sourcing`): same behavior; `SELECT seq, change_type, entity_type
FROM events ORDER BY seq` shows every write recorded. Import-on-existing-DB: with data present, restart with
`--campus-coffee.persistence.data-to-events-on-startup=true`, then with
`--campus-coffee.persistence.events-to-data-on-startup=true`, and confirm the tables are rebuilt from the
log (matching ids, business fields, and timestamps; the internal `@Version` counter resets).
