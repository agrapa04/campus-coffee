# Migrate ids from Long to UUID

Implementation plan. Standalone, behavior-preserving refactor. It is the clean baseline for the
event-sourcing change in `doc/2026-06-18_configurable-event-sourcing-persistence.md` (implement this one
first, verify green, commit, then do that one).

## Context

CampusCoffee's entity ids come from Postgres sequences (`<table>_seq`), assigned by the database on insert.
The event-sourcing work needs the **application** to assign the id *before* the write (so the event can be
the authoritative record), and we want ids that are deterministic and reproducible (for stable test data
and documentable README examples) without the sequence-reset machinery. Application-assigned `UUID`s
achieve both. This phase changes the id type and where it is assigned; relational behavior is otherwise
identical (the one added convenience is the dev-profile startup fixture load).

## Design

- **`IdGenerator` seam.** `fun interface IdGenerator { fun newId(): UUID; fun reset() {} }` in
  `domain/.../ports/`. The `data` `IdGeneratorConfiguration` picks the adapter from the
  `campus-coffee.id.seed` property: a numeric seed (default `42`) builds a `SeededUuidGenerator`
  (deterministic, backed by `java.util.Random`), and `random`/blank uses `UUID.randomUUID()`. The seam is
  injected into `CrudDataServiceImpl` and `ReviewApprovalDataServiceImpl`, which assign the id **in the
  data-layer insert path**. So `domain.id == null` still means "create" and a non-null id still means
  "update" (`findByIdOrNull` → load or `NotFoundException`), preserving the create-vs-update signal, the
  "PUT of a missing id → 404" behavior, and the rule that `POST` rejects a client-supplied id. `reset()`
  re-seeds the deterministic generator (a no-op for the random one); `DevController` calls it before
  reloading the fixtures, so each `PUT /api/dev/data` reassigns the same ids. The deterministic seed makes
  the loaded fixture ids reproducible (and so documentable in the README/INSTRUCTOR), which is what
  replaces the old sequence-reset machinery; a real deployment can set `CAMPUS_COFFEE_ID_SEED=random`.
  (The event-sourcing phase later renames this primary generator `entityIdGenerator` and adds a second,
  separately seeded `eventIdGenerator` for the event log's own ids; `SeededUuidGenerator.newId()` and
  `reset()` are also made `@Synchronized`, so a dev reload running concurrently with a write cannot perturb
  the deterministic sequence.)
- **Assigned ids + `Persistable`.** `Entity` (the `@MappedSuperclass`) holds a UUID id with **no**
  `@GeneratedValue`/`@CustomSequence` (the app assigns it), and implements `Persistable<UUID>` so
  `repository.save()` `persist`s a freshly built entity and `merge`s a loaded one with no redundant SELECT.
  Kotlin cannot override the generic `Persistable.getId()` with a property, and a property named `id` would
  collide with the `getId()` override on the JVM, so the id is a private `entityId` field exposed through
  `override fun getId()` / `fun setId()` (callers still write `entity.id`, which Kotlin surfaces from the
  Java interface's `getId()`/`setId()`). Newness is a private `@Transient persisted` flag flipped to `true`
  in `@PostLoad`/`@PostPersist`, with `override fun isNew() = !persisted`. Deliberately **not**
  `isNew = (createdAt == null)`: tying new-entity detection to the timestamp is fragile and breaks the
  moment `createdAt` is set together with the id (which the event-sourcing phase does). (The event-sourcing
  phase later extracts this id + `Persistable`/`isNew` handling into a `PersistableEntity` `@MappedSuperclass`
  that both `Entity` and the event log's `EventEntity` extend, so the assigned-id support lives in one place;
  `Entity` then holds only the timestamps.)
- **Timestamps unchanged here.** `createdAt`/`updatedAt` stay set by `@PrePersist`/`@PreUpdate` at persist
  time (relational mode never reads them before persist). The event-sourcing phase moves the stamp earlier;
  that is why `isNew` must not key off `createdAt`.
- **Delete the sequence machinery.** Remove `CustomSequence`, `CustomSequenceGenerator`,
  `ResettableSequenceRepository`, `ResettableSequenceRepositoryImpl`, `JpaUtils`; drop `repositoryBaseClass`
  from `RepositoryConfiguration` (default `SimpleJpaRepository`); remove `resetSequence()` from
  `CrudDataServiceImpl.clear()` and `ReviewApprovalDataServiceImpl.clear()`.
- **Type flips `Long` → `UUID`** at the concrete bindings only — the generic bases (`Identifiable<T>`,
  `DomainModel<ID>`, `CrudService`/`CrudDataService`/`CrudController`/`Dto`/`DtoMapper`) are already
  parameterized and do not change:
  - domain models `Pos`/`User`/`Review`/`ReviewApproval` `id`, plus `ReviewApproval.reviewId/userId`, and
    `DomainModel<Long>` → `<UUID>`;
  - concrete ports (`PosService`/`UserService`/`ReviewService`, `PosDataService`/`UserDataService`/
    `ReviewDataService`) `<…, Long>` → `<…, UUID>` and explicit id params (`getById`, `delete`,
    `ReviewService.filter(posId)`, `approve(reviewId)`, `ReviewApprovalDataService.countByReviewId`,
    `UserService.getById(id, actingUser)`);
  - DTOs (`PosDto`/`UserDto`/`ReviewDto` `id`, `ReviewDto.posId`/`authorId`);
  - controllers (`@PathVariable id`, `@RequestParam("pos_id")`);
  - `ReviewDtoMapper` resolves `posService.getById(posId)` (now `UUID`) — no code change, just the type;
  - entity FK columns `ReviewEntity.pos/author`, `ReviewApprovalEntity.reviewId/userId` become `uuid`.
- **Stays `Long` (do NOT change):** the OSM `nodeId` everywhere (`OsmNode`, `OsmClient`, `OsmResponse`,
  `OsmDataService.fetchNode`, `PosService.importFromOsmNode`, `/pos/import/osm/{nodeId}`) — it is an
  external OpenStreetMap id; `ReviewEntity.@Version` (optimistic-lock counter); `approvalCount`.
- **Migrations — rewrite V1, V2, V3, V7 in place** (teaching repo, no production data): `id bigint` → `id uuid`
  PRIMARY KEY; FK columns (`pos_id`, `author_id`, `review_id`, `user_id`) `bigint` → `uuid`; drop the
  `*_seq` sequences (incl. `review_approvals_seq`). No DB default (`gen_random_uuid()`) — the app always
  assigns the id. V4/V5/V6 (version column, the pos+author unique constraint, postal-code) are unaffected
  except that V5's referenced columns are now `uuid` (handled by the V3 rewrite). `ddl-auto: validate`
  stays (the rewritten schema matches the entities).

## Files

- **domain:** `model/objects/{Pos,User,Review,ReviewApproval}.kt`; `ports/data/ReviewApprovalDataService.kt`
  (`countByReviewId` param); `ports/api/{UserService,ReviewService}.kt`;
  `implementation/{Pos,User,Review}ServiceImpl.kt`; new `ports/IdGenerator.kt`;
  `tests/TestFixtures.kt` (UUID fixture ids; `createReviewFixtures` re-points reviews at the persisted
  POS/users). (`Identifiable`/`DomainModel`/`CrudService` unchanged — generic.)
- **api:** `dtos/{PosDto,UserDto,ReviewDto}.kt`; `controller/{Pos,User,Review}Controller.kt`;
  `controller/DevController.kt` (inject `IdGenerator`, `reset()` before reloading the fixtures). (mappers unchanged.)
- **data:** `persistence/entities/Entity.kt` (UUID + `Persistable` + drop generation); `ReviewApprovalEntity.kt`;
  `persistence/repositories/{Pos,User,Review,ReviewApproval}Repository.kt` (`<_, UUID>`, drop
  `ResettableSequenceRepository`); `configuration/RepositoryConfiguration.kt` (drop `repositoryBaseClass`);
  new `configuration/IdGeneratorConfiguration.kt` and `configuration/SeededUuidGenerator.kt`;
  `implementations/CrudDataServiceImpl.kt` (assign id in the insert path; drop `resetSequence`);
  `implementations/ReviewApprovalDataServiceImpl.kt`. **Delete:**
  `persistence/generators/CustomSequence*.kt`, `persistence/repositories/ResettableSequenceRepository*.kt`,
  `util/JpaUtils.kt`. Mappers: no change. Migrations `db/migration/V1,V2,V3,V7` rewritten.
- **application:** `application.yaml` — add `campus-coffee.id.seed` (default `42`, env-overridable to
  `random`) and turn on the `dev`-profile `fixtures.load-on-startup`, so the dev app comes up with the
  seeded fixtures already loaded.

## Tests

Most tests **round-trip** the returned id and are unaffected. The ones that change, and the seam:

- **Deterministic ids for tests:** no `@TestConfiguration` is needed. `campus-coffee.id.seed` (default `42`)
  makes the seeded generator the default everywhere, so the system tests (which load `application.yaml`)
  and the data integration tests (which fall back to the `@Value` default) get reproducible ids.
- `domain/.../tests/TestFixtures.kt`: the reference `USER_LIST`/`POS_LIST`/`REVIEW_LIST` ids become `UUID`
  via a `fixtureId(n) = UUID(0L, n)` helper (the `*ForInsertion()` helpers strip them before seeding).
  `createReviewFixtures` re-points each fixture review at the **persisted** POS and author (matched by POS
  name and login name), because the persisted ids are assigned at insertion and no longer equal the
  fixture reference ids.
- **Direct repository saves:** tests that bypass the data services and save an entity straight through a
  repository (`PosRepositoryIntegrationTest`, `OptimisticLockingIntegrationTest`,
  `ReviewRepositoryIntegrationTest`, `PosEntityMapperRoundTripTest`) assign an id via a `withGeneratedId()`
  helper on `AbstractDataIntegrationTest`, since the database no longer generates one.
- `PosServiceTest`/`ReviewServiceTest`/`ReviewDtoMapperTest`/`CampusUserDetailsServiceTest`: replace `Long`
  literals with a fixed `UUID(0L, n)`. `CrudDataServiceOptimisticLockTest` passes an `IdGenerator` to the
  `PosDataServiceImpl` constructor.
- "ghost" ids (404 cases) in `ReviewSystemTests`/`ErrorPathSystemTests`/`ReviewServiceTest`: a deterministic
  `UUID(0L, n)` that no fixture carries.
- `SystemTestUtils`: `Requests<T>` id signatures `Long` → `UUID` (`idGetter`, `retrieveById`, the
  delete/update/approve id helpers).
- `ResettableSequenceIntegrationTest`: **deleted**. New `IdGeneratorConfigurationTest` (random-vs-seeded
  selection, determinism, `reset`, and the seed-`42` ids the docs reference); `DevSystemTests` also asserts
  that a reload reassigns the same ids.
- Acceptance `.feature`/Cucumber steps: unaffected (no literal ids).
- Coverage gate (95/82): re-run `gradle build` and confirm the gate holds.

## Docs, changelog & version

- **`CHANGELOG.md`** (Keep a Changelog): add an `## [Unreleased]` entry for the UUID id migration
  (app-assigned ids via `IdGenerator` and the `campus-coffee.id.seed` selection, the custom-sequence
  machinery removed). The version is tracked only in
  the CHANGELOG headings (latest `0.2.0`; there is no `version` in `gradle.properties`/`build.gradle.kts`),
  so a bump = adding the next `## [x.y.z]` heading — this is an internal refactor, so the `[Unreleased]`
  block suffices.
- **`CLAUDE.md`** (must change): replace the *Custom Sequence Generation* section with an *Identifier
  Generation* section (the `IdGenerator` port, the `campus-coffee.id.seed` selection, the seeded/random
  adapters, `Persistable`, and `reset` on the dev reload); update the Database/Migrations notes and the
  dev-profile run notes (the dev app now loads the fixtures on startup).
- **`README.md`** / **`INSTRUCTOR.md`**: the `curl` examples referenced ids by small number throughout (not
  minimal). They now use the concrete seeded fixture UUIDs (e.g. `jane_doe` = `ba419d35-…`, with a `#`
  comment naming the entity), and both note that the `dev` app loads the fixtures on startup, so the first
  manual `PUT /api/dev/data` is no longer required.

## Verification

`gradle build` green (all existing suites pass against UUID, plus the new id-generator tests). The `dev`
profile loads the fixtures on startup; `PUT /api/dev/data` reloads them, reassigning the same seeded ids.
Confirm responses and the DB now carry `uuid` ids. The id behavior is otherwise identical to before (the
dev startup load is the one added convenience).
