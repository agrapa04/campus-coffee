# Instructor demo: event sourcing (event-first CQRS) persistence

This guide walks through the **event-sourced** persistence mode, where an append-only **event log** is the
source of truth and the relational tables are a **read model** projected from it. The point of the
walkthrough is architectural: the **`domain` and `api` modules did not change at all** — the entire feature
lives in the **`data` module**, behind the same port the relational adapter implements. The companion
authentication/authorization demo is in [`INSTRUCTOR_AUTH.md`](INSTRUCTOR_AUTH.md).

Event sourcing is the **default** (`campus-coffee.persistence.mode = event-sourcing`); pass
`--campus-coffee.persistence.mode=relational` for the plain relational adapter. Both pass the same system
tests, so the API behaves identically — only *how* a write is stored differs.

## 1. The shape: one port, two interchangeable adapters

CampusCoffee is hexagonal (ports and adapters). The **`domain`** defines a data **port** and depends only on
it; a **`data`** adapter implements it. For POS the port is `PosDataService` — a plain interface with no
persistence detail and no notion of event sourcing:

```kotlin
// domain/.../ports/data/PosDataService.kt  — unchanged by this feature
interface PosDataService : CrudDataService<Pos, UUID> {
    fun getByName(name: String): Pos
}
```

There are now **two adapters** for that one port, selected at startup by `campus-coffee.persistence.mode`:

- `relational` → `PosDataServiceImpl` (`@Service`) writes straight to the `pos` table.
- `event-sourcing` (default) → `EventSourcedPosDataService` appends an event and projects it.

The domain service (`PosServiceImpl`) is injected with the `PosDataService` port and never learns which
adapter it received. That is exactly why the `domain` and `api` layers needed no change.

## 2. The domain module did not change

The feature is confined to `data` plus one Flyway migration. You can confirm it straight from git — the
commit that introduced the mode touched nothing under `domain/` or `api/`:

```shell
ES=$(git log -1 --format=%H --grep='configurable event-sourcing')
git show --stat "$ES" -- domain api      # -> empty: no domain/ or api/ files changed
git show --stat "$ES" -- data | tail     # -> the whole change is under data/ (+ the V8 migration)
```

The reason it *can* be confined: the domain talks to the `PosDataService` **port**, and both adapters
implement it. Choosing an adapter is a wiring decision inside `data`, invisible to everything above it.

## 3. How the data module changed

A new package — `data/src/main/kotlin/de/seuhd/campuscoffee/data/persistence/eventsourcing/` — holds the
whole mechanism:

| File | Role |
|------|------|
| `EventEntity` / `EventRepository` + `V8` `events` table | the append-only log (one full-state event per write) |
| `EventStore` | appends INSERT/UPDATE/DELETE events; owns the JSON body (drops the raw password, flattens a review's POS/author to ids) |
| `ReadModelProjector` | applies one event to the read tables, reusing the MapStruct mappers and preserving the id + timestamps |
| `EventSourcedWriter` | the shared event-first write: assign id/timestamps → append event → project, in one transaction |
| `EventSourced{Pos,User,Review,ReviewApproval}DataService` | the decorators, one per port |
| `DataToEventsRunner` / `EventsToDataRunner` | import an existing database into the log / rebuild the tables from the log |
| `PersistenceProperties` (in `data/configuration`) | binds the `campus-coffee.persistence.*` flags |

### The decorator

Each event-sourced adapter is a **Decorator** (the design pattern) around the relational impl. Reads and
queries auto-delegate (`by delegate`); only the writes are overridden. It is
`@Primary @ConditionalOnProperty(... "event-sourcing")`, so the domain binds to it instead of the relational
adapter only when the mode is on:

```kotlin
@Service
@Primary
@ConditionalOnProperty(
    name = [PersistenceProperties.MODE_PROPERTY],
    havingValue = PersistenceProperties.EVENT_SOURCING_MODE,
    matchIfMissing = true,                     // a missing key still activates it (the default)
)
class EventSourcedPosDataService(
    private val delegate: PosDataServiceImpl,   // the relational adapter, wrapped
    private val writer: EventSourcedWriter,
) : PosDataService by delegate {                // reads + getByName delegate straight through

    @Transactional
    override fun upsert(domain: Pos): Pos =
        writer.upsert(
            domain,
            delegate::getById,
            { id, now -> domain.copy(id = id, createdAt = now, updatedAt = now) },        // create
            { existing, now -> domain.copy(createdAt = existing.createdAt, updatedAt = now) }, // update
        )

    @Transactional
    override fun delete(id: UUID) = writer.delete(Pos::class, id, delegate::getById)
}
```

The relational adapter's `upsert` (inherited from `CrudDataServiceImpl`) does the opposite: it maps the
domain object to a `PosEntity` and `save`s it directly — no event.

### Event-first write, in one transaction

`EventSourcedWriter` is where "the event is the source of truth" becomes literal. It appends the event
**then** projects it onto the read table, both inside the decorator's `@Transactional`, so a constraint
violation in the projection rolls the event back too — the log can never hold a write the read model rejected:

```kotlin
val complete = buildForInsert(idGenerator.newId(), now)  // complete event body: assigned id + timestamps
val event = eventStore.appendInsert(complete)            // 1) append to the log (the source of truth)
project(event)                                           // 2) project onto the read table (enforces uq/FK/@Version)
return getById(complete.id)                              // read the projected row back
```

The id comes from the **same** `IdGenerator` the relational mode uses, so the assigned entity ids are
identical across modes (a separate generator with its own seed assigns the event ids).

## 4. Run it and watch each write become an event

Start the app and database (the dev profile loads the fixtures; the default mode is event sourcing):

```shell
docker compose up --build
```

The fixture load writes **through** the log, so the `events` table already holds one event per seeded row
(5 users, 4 POS, 3 reviews); `seq` is the append order:

```shell
docker exec -it db psql -U postgres -c \
  "SELECT seq, change_type, entity_type, entity_version FROM events ORDER BY seq;"
```

Make an authenticated write — a moderator creates a POS (the accounts are listed in `INSTRUCTOR_AUTH.md`):

```shell
curl -i --request POST -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn --header "Content-Type: application/json" \
  --data '{"name":"Event Cafe","description":"Demo","type":"CAFE","campus":"ALTSTADT","street":"Hauptstrasse","houseNumber":"5","postalCode":"69117","city":"Heidelberg"}' \
  http://localhost:8080/api/pos
```

A new `INSERT` / `Pos` event is appended, and the `pos` read table is projected from it — the id and
timestamps in the event `body` match the row:

```shell
docker exec -it db psql -U postgres -c \
  "SELECT seq, change_type, entity_type, body->>'id' AS id, body->>'name' AS name
   FROM events WHERE entity_type='Pos' ORDER BY seq DESC LIMIT 1;"
```

The read still comes from the table, not a replay — `GET /api/pos` serves the projected row directly:

```shell
curl -s http://localhost:8080/api/pos | python3 -c 'import sys,json; print([p for p in json.load(sys.stdin) if p["name"]=="Event Cafe"])'
```

### The log is authoritative: a rejected write leaves no event

Because the append and the projection share one transaction, a constraint violation rolls **both** back.
Repeat the create — the name now duplicates the row just added:

```shell
curl -i --request POST -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn --header "Content-Type: application/json" \
  --data '{"name":"Event Cafe","description":"Dup","type":"CAFE","campus":"ALTSTADT","street":"Hauptstrasse","houseNumber":"6","postalCode":"69117","city":"Heidelberg"}' \
  http://localhost:8080/api/pos
# -> 409 Conflict (duplicate name)
```

Re-run the events query: the count is unchanged — the failed write appended nothing.

## 5. Relational mode for comparison

Restart in relational mode. The same API does the same thing, but writes go straight to the tables and
nothing is logged:

```shell
docker compose down
docker run -d --name db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18-alpine
gradle :application:bootRun --args='--spring.profiles.active=dev --campus-coffee.persistence.mode=relational'

docker exec -it db psql -U postgres -c "SELECT count(*) FROM events;"   # -> 0 in relational mode
```

The `events` table still exists (`V8` always runs), but nothing writes to it, and the read tables are the
only copy of the data.

## 6. Migrating an existing relational database into the log

Two one-shot startup flags move an existing database between the two representations (both act only in
event-sourcing mode):

```shell
# 1) import: append one INSERT event per existing row (idempotent per type)
gradle :application:bootRun --args='--spring.profiles.active=dev --campus-coffee.persistence.data-to-events-on-startup=true'

# 2) rebuild: clear the tables and replay the whole log back into them
gradle :application:bootRun --args='--spring.profiles.active=dev --campus-coffee.persistence.events-to-data-on-startup=true'
```

The rebuild reconstructs the ids, business fields, and `createdAt`/`updatedAt` from the events (the internal
`@Version` counter restarts at zero, which has no observable effect). Both runners are `StartupTask`s that
run **before** the web server accepts requests, so the API is never served from half-built tables, and the
rebuild refuses to run against an empty log (it would otherwise clear a populated read model with nothing to
replay).

## Where to look in the code

- **Port (unchanged):** `domain/.../ports/data/PosDataService.kt`, `domain/.../ports/data/CrudDataService.kt`
- **Relational adapter:** `data/.../implementations/PosDataServiceImpl.kt` (and the generic `CrudDataServiceImpl.kt`)
- **Event-sourcing package:** `data/.../persistence/eventsourcing/` — the decorators, `EventStore`, `ReadModelProjector`, `EventSourcedWriter`, and the import/rebuild runners
- **Migration:** `data/.../db/migration/V8__create_events_table.sql`
- **Toggle:** `campus-coffee.persistence.mode` (`PersistenceProperties`)
- **Design notes:** `doc/2026-06-18_configurable-event-sourcing-persistence.md`
