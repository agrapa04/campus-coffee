# Migrate ids from Long to UUID

Implementation plan. Standalone, behavior-preserving refactor. It is the clean baseline for the
event-sourcing change in `doc/2026-06-18_configurable-event-sourcing-persistence.md` (implement this one
first, verify green, commit, then do that one).

## Context

CampusCoffee mints entity ids from Postgres sequences (`<table>_seq`), assigned by the database on insert.
The event-sourcing work needs the **application** to mint the id *before* the write (so the event can be
the authoritative record), and we want ids that are deterministic in tests without the sequence-reset
machinery. Application-minted `UUID`s achieve both. This phase changes only the id type and where it is
minted; relational behavior is otherwise identical.

## Design

- **`IdGenerator` seam.** `fun interface IdGenerator { fun newId(): UUID }` in `domain/.../ports/`;
  production bean `IdGenerator { UUID.randomUUID() }` in a `data` `@Configuration`. Inject it into
  `CrudDataServiceImpl` and `ReviewApprovalDataServiceImpl`, and mint the id **in the data-layer insert
  path**. So `domain.id == null` still means "create" and a non-null id still means "update"
  (`findByIdOrNull` → load or `NotFoundException`). This preserves the create-vs-update signal, the
  "PUT of a missing id → 404" behavior, and the rule that `POST` rejects a client-supplied id. Tests bind
  a deterministic generator (below).
- **Assigned ids + `Persistable`.** `Entity` (the `@MappedSuperclass`) becomes `id: UUID?` with **no**
  `@GeneratedValue`/`@CustomSequence` (the app assigns the id), and implements `Persistable<UUID>` with an
  explicit transient flag: `@Transient var isNew = true`, flipped to `false` in `@PostLoad`/`@PostPersist`,
  `override fun isNew() = isNew`. This makes `repository.save()` `persist` a freshly built entity and
  `merge`/update a loaded one, with no redundant SELECT. Deliberately **not** `isNew = (createdAt == null)`:
  tying new-entity detection to the timestamp is fragile and breaks the moment `createdAt` is set together
  with the id (which the event-sourcing phase does).
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
- **Migrations — rewrite V1–V7 in place** (teaching repo, no production data): `id bigint` → `id uuid`
  PRIMARY KEY; FK columns (`pos_id`, `author_id`, `review_id`, `user_id`) `bigint` → `uuid`; drop the
  `*_seq` sequences (incl. `review_approvals_seq`). No DB default (`gen_random_uuid()`) — the app always
  assigns the id. V4/V5/V6 (version column, the pos+author unique constraint, postal-code) are unaffected
  except that V5's referenced columns are now `uuid` (handled by the V3 rewrite). `ddl-auto: validate`
  stays (the rewritten schema matches the entities).

## Files

- **domain:** `model/objects/{Pos,User,Review,ReviewApproval}.kt`; `ports/data/ReviewApprovalDataService.kt`
  (`countByReviewId` param); `ports/api/{UserService,ReviewService}.kt`;
  `implementation/{Pos,User,Review}ServiceImpl.kt`; new `ports/IdGenerator.kt`.
  (`Identifiable`/`DomainModel`/`CrudService` unchanged — generic.)
- **api:** `dtos/{PosDto,UserDto,ReviewDto}.kt`; `controller/{Pos,User,Review}Controller.kt`. (mappers unchanged.)
- **data:** `persistence/entities/Entity.kt` (UUID + `Persistable` + drop generation); `ReviewApprovalEntity.kt`;
  `persistence/repositories/{Pos,User,Review,ReviewApproval}Repository.kt` (`<_, UUID>`, drop
  `ResettableSequenceRepository`); `configuration/RepositoryConfiguration.kt` (drop `repositoryBaseClass`);
  new `configuration/IdGeneratorConfiguration.kt`; `implementations/CrudDataServiceImpl.kt` (mint in insert
  path; drop `resetSequence`); `implementations/ReviewApprovalDataServiceImpl.kt`. **Delete:**
  `persistence/generators/CustomSequence*.kt`, `persistence/repositories/ResettableSequenceRepository*.kt`,
  `util/JpaUtils.kt`. Mappers: no change. Migrations `db/migration/V1,V2,V3,V7` rewritten.

## Tests

Most tests **round-trip** the returned id and are unaffected. The ones that break, and the seam:

- **Deterministic `IdGenerator` for tests:** in `AbstractDataIntegrationTest` and `AbstractSystemTest`, bind
  (via `@TestConfiguration`/bean override) a sequential generator, e.g. `UUID(0L, counter++)`, so created
  ids are reproducible per test.
- `domain/.../tests/TestFixtures.kt`: the reference `USER_LIST`/`POS_LIST`/`REVIEW_LIST` ids become `UUID`
  (or `null` — the `*ForInsertion()` helpers already strip ids, so seeding is unaffected).
- `PosServiceTest`: replace literal `1L` in mock setup with a fixed test `UUID` (or `any()`).
- `ReviewSystemTests`: replace the `9999L` "ghost" id (404 cases) with a random non-existent `UUID`.
- `SystemTestUtils`: `Requests<T>` id signatures `Long` → `UUID` (`idGetter`, `retrieveById`, the
  delete/update id-list helpers).
- `ResettableSequenceIntegrationTest`: **delete** (the feature is gone); optionally add a tiny `IdGenerator` test.
- Acceptance `.feature`/Cucumber steps: unaffected (no literal ids).
- Coverage gate (95/82): deleting the sequence classes/tests shifts the ratio; the new
  `IdGenerator`/`Persistable` are tiny — re-run `gradle build` and confirm the gate holds.

## Docs, changelog & version

- **`CHANGELOG.md`** (Keep a Changelog): add an `## [Unreleased]` entry for the UUID id migration
  (app-minted ids via `IdGenerator`, the custom-sequence machinery removed). The version is tracked only in
  the CHANGELOG headings (latest `0.2.0`; there is no `version` in `gradle.properties`/`build.gradle.kts`),
  so a bump = adding the next `## [x.y.z]` heading — this is an internal refactor, so the `[Unreleased]`
  block suffices.
- **`CLAUDE.md`** (must change): replace the *Custom Sequence Generation* section, which documents the
  `data/.../persistence/generators/` classes this phase deletes, with the `IdGenerator` + UUID +
  `Persistable` approach; scan the Database/Migrations notes and any `/{id}` examples for id/sequence
  references and update.
- **`README.md`** / **`INSTRUCTOR.md`**: carefully review (they document run/deploy flows and examples) — a
  grep found no id-type references in `README.md`, so likely minimal, but confirm no example shows a numeric
  id and update if so.

## Verification

`gradle build` green (all existing suites pass against UUID). Run dev
(`gradle :application:bootRun --args='--spring.profiles.active=dev'`), `PUT /api/dev/data`, and confirm
responses and the DB now carry `uuid` ids. Behavior is otherwise identical to before.
