# Configurable event-sourcing (event-first CQRS) persistence

Implementation plan. **Prerequisite:** the id migration in `doc/2026-06-18_migrate-ids-to-uuid.md` is done
(ids are application-minted `UUID`s and an `IdGenerator` bean exists). Implement that first.

## Context

Add a second, genuinely event-sourced data layer, selectable by a config flag, mirroring the
`ase24-taskboard` approach (whose event-sourcing impl appended the event and a database trigger derived the
table). We use **event-first** writes: the event log is the source of truth and the relational tables are a
projection derived from it, so the "source of truth" framing is accurate rather than overclaimed. The
projection moves from taskboard's SQL triggers into testable Kotlin, reusing the existing MapStruct mappers
(the decoupling-via-mappers lesson). Reads stay fast — served from the materialized tables, no
replay-on-read. Default mode stays relational; the `domain` and `api` layers are unchanged.

## Design

- **Write = event-first, in one transaction.** The event-sourced `*DataService` is a decorator
  (`: PosDataService by delegate`, so reads/queries auto-delegate to the relational impl). Its mutating
  methods do NOT call `delegate.upsert`; they: mint the id via `IdGenerator` and stamp `createdAt`/
  `updatedAt` (on create) so the event body is complete, **append the event** (the authoritative write),
  then **project** the change into the relational table — all in one `@Transactional` method. The projector
  writes those exact id and timestamps, bypassing `@PrePersist`/`@PreUpdate` so they are not clobbered (the
  entity's transient `isNew` flag, not `createdAt`, drives persist-vs-update). The projection's table write
  enforces `uq_*`/FK/`@Version`; a violation rolls back the whole transaction, so the log never holds an
  invalid event. (Invariants are thus enforced by the projection's DB constraints — a pragmatic choice that
  keeps the event authoritative while reusing the DB's enforcement.)
- **`EventStore` + `EventEntity` + `EventRepository`** (package `data.eventsourcing`): one generic
  full-state event. `EventEntity`: `changeType: ChangeType {INSERT,UPDATE,DELETE}`, `entityType: String`,
  `entityVersion: Long`, `body: Map<String, Any>` (`@JdbcTypeCode(SqlTypes.JSON)`), `createdAt`; own
  `id: UUID` minted via `IdGenerator`. The domain object's own id lives inside `body` (no separate id
  column; no `created_by` — the data layer can't see the actor). `entityType` is derived from
  `domain::class.simpleName` (and a `KClass` token for delete/clear, so there is one source). `EventStore`
  owns the single JSON authority: one `ObjectMapper` set as Hibernate's `hibernate.type.json_format_mapper`
  so the `Map` and the `jsonb` column use the same mapper; mixins `@JsonIgnore` `entityVersion`, **always**
  the raw `User.password`, and flatten `Review`'s nested `Pos`/`User` to their ids (so no `passwordHash`
  leaks via review events; `User` events retain `passwordHash`, documented, so login survives a rebuild).
- **`ReadModelProjector`** (data): applies one event to the tables — INSERT (build the entity via the
  mapper, set the id and `createdAt`/`updatedAt` from the event body, persist bypassing the `@PrePersist`
  timestamp callback so the row matches the body; the transient `isNew` flag makes it a `persist`), UPDATE
  (load + `updateEntity` + write the body's `updatedAt`), DELETE (`repository.delete`, FK violation →
  `DeletionConflictException`). Reused by the decorators' live writes **and** by the events-to-data replay,
  so the id/timestamp-preserving insert lives in one place.
- **Toggle:** `campus-coffee.persistence.mode = relational (default) | event-sourcing`. The relational
  impls stay plain `@Service`; each decorator is `@Service @Primary
  @ConditionalOnProperty(... "event-sourcing")`, injecting the concrete relational impl as `delegate` plus
  the `EventStore`/`ReadModelProjector`/`IdGenerator`. A `PersistenceProperties
  @ConfigurationProperties("campus-coffee.persistence")` holds `mode`, `dataToEventsOnStartup`,
  `eventsToDataOnStartup` (fail-fast on a bad mode); the runners read it.
- **events → data** (rebuild): a flag-gated (`events-to-data-on-startup`) `ApplicationReadyEvent`
  data-component clears the tables and replays the whole log (`findAllByOrderByIdAsc`) through the
  `ReadModelProjector`, preserving ids + `createdAt`/`updatedAt` (bypass `@PrePersist` for the replay;
  `@Version` resets, which is harmless). No `api` involvement.
- **data → events** (adopt an existing database): a flag-gated, `@Transactional` `ApplicationReadyEvent`
  component reads the current rows and appends one INSERT event each in FK order (users/pos → reviews →
  approvals; **approvals via `ReviewApprovalRepository.findAll()`** since that port has no `getAll`);
  idempotent per type (skip when the type's log is non-empty).
- **Migration V8** creates the `events` table (`id uuid` PK, `change_type`, `entity_type`, `entity_version`,
  `body jsonb`, `created_at`). Always runs (the entity is always mapped).
- **Reuses from the id migration:** `IdGenerator` (minting) and `Persistable`/`isNew` (id-preserving inserts
  in the projector).

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
- **Startup-runner tests (for the thin branch margin):** boot a context with each flag on so the gated
  `@EventListener(ApplicationReadyEvent)` bodies and branches execute — `data-to-events-on-startup=true` in
  ES mode (happy path) and run twice for the idempotent skip; `events-to-data-on-startup=true` in ES mode
  (rebuild) and once in **relational** mode for the log-and-skip branch; and a both-flags-set boot for the
  ordering. (Fallback if booting is awkward: keep each runner a thin shell delegating to an already-covered
  method.)
- **`PersistenceModeWiringTest`:** the injected `*DataService` beans are the relational impls in default
  mode and the `EventSourced*` decorators under `mode=event-sourcing`.
- **`EventSourcingModeSystemTests`** (its own base with the mode property; do **not** extend
  `AbstractSystemTest`, whose cached context lacks it): a representative subset under `mode=event-sourcing`
  — the happy path, an authenticated (JWT) write, an update/edit, the OSM import, plus the key error cases
  (duplicate 409, second review 409, double-approve 409, self-approval 400, delete referenced POS 409,
  delete review twice 204/404).

ArchUnit: the new code is in the `data` layer and imports only `domain` + Spring/Jackson; no rule trips.

## Docs, changelog & version

- **`CHANGELOG.md`** (Keep a Changelog): add an `[Unreleased]` entry for the configurable
  event-sourcing/CQRS persistence mode and, since this is a user-facing feature, open the next
  `## [x.y.z]` section (version is tracked only via CHANGELOG headings, latest `0.2.0`).
- **`CLAUDE.md`:** Ports & Adapters (the two interchangeable data adapters + the mode flag), Build/Run (the
  ES run line), Database (`V8` + the always-present `events` table), Testing (both-modes note), Configuration
  (the three `campus-coffee.persistence.*` properties). Note the design honestly: events = source of truth,
  the tables = a materialized read model rebuilt from the log; the events table retains `passwordHash` (same
  sensitivity as the `users` table) but never the raw `password`.
- **`README.md`:** add the ES run line (`--campus-coffee.persistence.mode=event-sourcing`) and the
  data-to-events → events-to-data adoption flow. **`INSTRUCTOR.md`:** add a demo step (switch to ES mode,
  inspect the `events` table) or note it is intentionally omitted.
- **PITest:** no glob change — `de.seuhd.campuscoffee.data.*` already matches `data.eventsourcing.*`.

## Risks / verify first

- **`Map` ↔ `jsonb` round-trip with one `FormatMapper`.** Spring Boot 4 carries two Jackson majors; with
  `@JdbcTypeCode(SqlTypes.JSON)` Hibernate re-serializes the `Map` with whichever `FormatMapper` it picks.
  Make one mapper authoritative (`hibernate.type.json_format_mapper` = the `EventStore` mapper, or store
  `body` as a pre-serialized `String`). Write a round-trip test (persist a body with `LocalDateTime`
  timestamps, read back, replay, assert they survive) **before** building on it.
- **Transaction boundary.** The event append + the projection must share one transaction so a constraint
  violation rolls back both and the log never holds an invalid event; verify the append participates and
  `saveAndFlush` surfaces violations inside the method.
- **Branch-coverage margin is thin** (~0.82 today); the runner tests above must execute the skip/idempotent/
  both-flags branches or the gate fails. Re-run `gradle build` and confirm ≥ 0.82.
- **`events → data` fidelity:** ids preserved via the `Persistable` save; timestamps written from the body
  (bypass `@PrePersist`); `@Version` resets (harmless). Keep the test comparison consistent with that.

## Verification

`gradle build` green (both modes). Relational run unchanged. ES run
(`--campus-coffee.persistence.mode=event-sourcing`): same behavior; `SELECT change_type, entity_type FROM
events ORDER BY id` shows every write recorded. Adopt-on-existing-DB: with data present, restart with
`--campus-coffee.persistence.data-to-events-on-startup=true`, then with
`--campus-coffee.persistence.events-to-data-on-startup=true`, and confirm the tables are rebuilt from the
log (matching ids, business fields, and timestamps; the internal `@Version` counter resets).
