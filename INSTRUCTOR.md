# Instructor demo: authentication and authorization

This guide demonstrates HTTP Basic authentication, and the access-control matrix built on top of it,
with CampusCoffee using `docker compose` and `curl`. It targets the reference solution, where the assignment's exercises
are implemented and authentication is enforced. The first demo (steps 1 to 6) runs the application
locally; the second (step 7) deploys it to Google Cloud Run and runs the same calls against a public
HTTPS URL.

> Note: the unmodified student starter does not enforce authentication, so the `401` and `403` responses
> below appear only after Exercises 1 to 3 are done. This file belongs to the reference solution, not the
> student starter.

## 1. Build and run with Docker Compose

`compose.yaml` builds the application image and runs it with a PostgreSQL container in the dev profile:

```shell
# build the image and start the app and the database; the dev profile loads the fixture
# data (users, POS, reviews) on startup
docker compose up --build
```

The base URL is `http://localhost:8080/api`. Swagger UI is at
`http://localhost:8080/api/swagger-ui.html`. The `/api/dev` endpoints (no credentials needed) let you
reload or clear the fixture data; see "Reset the local demo" below.

To run without Docker, start a PostgreSQL container and use Gradle instead:

```shell
docker run -d --name db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18-alpine
gradle :application:bootRun --args='--spring.profiles.active=dev'
```

## 2. The accounts

The fixture data includes five users with known passwords. `USER` is the base; `MODERATOR` (content
moderation) and `ADMIN` (user administration) are independent grants on top, so `jane_doe` holds all three
while `olivia_admin` holds `USER` and `ADMIN` but not `MODERATOR`. The same list is in the README's *dev* section.

| Login name      | Password               | Roles                        |
|-----------------|------------------------|------------------------------|
| `student2023`   | `ZwTwB8Hn8VkNLZec7bR1` | `USER`                       |
| `lisa_lee`      | `lG6v9dGKZA5kfOHTFLNR` | `USER`                       |
| `maxmustermann` | `AmLtoD3r8lVdnwoLN1Nn` | `USER`, `MODERATOR`          |
| `jane_doe`      | `aaaMbnPdFYDqkOpS3fVA` | `USER`, `MODERATOR`, `ADMIN` |
| `olivia_admin`  | `Qp7r2sV9xKmN4bLdTtYw` | `USER`, `ADMIN`              |

`curl` sends Basic credentials with `-u <login>:<password>`.

Resource ids are `UUID`s the server assigns on creation. With the default seed (`campus-coffee.id.entity-seed` =
`42`), a freshly loaded fixture dataset always gets the same ids, so the commands below use the concrete
fixture ids: `jane_doe` is `ba419d35-0dfe-8af7-aee7-bbe10c45c028`, `student2023` is
`aa616abe-1761-0c9a-e743-67bd738597dc`, the `Café Botanik` POS is `2d68ad16-268a-478c-9827-50f4569b5949`,
and the review `student2023` authored (of `New Vending Machine`) is `947c82ee-1735-c9ed-c0a4-7deecc7229ce`.
If you have changed the data, read the current ids from `GET /api/users`, `GET /api/pos`, or
`GET /api/reviews`.

## 3. Authentication: read requests are public, write requests need credentials

A read request is public and needs no credentials:

```shell
curl http://localhost:8080/api/pos
```

An unauthenticated write request is rejected with `401` (use `-i` to see the status line):

```shell
curl -i --request POST --header "Content-Type: application/json" \
  --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"Great flat white!"}' \
  http://localhost:8080/api/reviews
# -> HTTP/1.1 401 Unauthorized, JSON ErrorResponse body
```

Creating a user needs no credentials, because a user cannot authenticate before the account exists:

```shell
curl -i --request POST --header "Content-Type: application/json" \
  --data '{"loginName":"demo_user","emailAddress":"demo@uni-heidelberg.de","firstName":"Demo","lastName":"User","password":"demo-password"}' \
  http://localhost:8080/api/users
# -> 201 Created; the response never contains the password or its hash
```

The same write request with valid credentials succeeds, and the review is authored by the caller, with
no `authorId` in the body:

```shell
curl -i --request POST -u student2023:ZwTwB8Hn8VkNLZec7bR1 --header "Content-Type: application/json" \
  --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"Great flat white!"}' \
  http://localhost:8080/api/reviews
# -> 201 Created; "author" is student2023
```

A wrong password is rejected with `401`:

```shell
curl -i --request POST -u student2023:wrong-password --header "Content-Type: application/json" \
  --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"..."}' \
  http://localhost:8080/api/reviews
# -> 401 Unauthorized
```

## 4. Authorization: the three roles

`SecurityConfig` holds the access rules as an ordered `authorizeHttpRequests` block. The coarse,
URL-based rules are declared there; the finer, per-target rules (a review's author, a user editing or reading only
their own account) are enforced in the domain services, because they depend on *which* row is targeted.

```kotlin
authorizeHttpRequests {
    authorize("/api/dev/**", permitAll)
    authorize(HttpMethod.POST, "/api/users", permitAll)             // open registration
    authorize("/api/auth/token", permitAll)
    authorize(HttpMethod.GET, "/api/users", hasRole("ADMIN"))       // listing users exposes PII -> admin-only
    authorize(HttpMethod.GET, "/api/users/**", authenticated)       // one user: self or admin (domain decides)
    authorize(HttpMethod.GET, "/**", permitAll)                     // POS and review reads are public
    authorize(HttpMethod.POST, "/api/pos/**", hasRole("MODERATOR")) // and PUT, DELETE
    authorize(HttpMethod.DELETE, "/api/users/**", hasRole("ADMIN"))
    authorize(anyRequest, authenticated)                            // every other write request
}
```

Spring evaluates these rules top to bottom and applies the first match, so the specific matchers come
before `anyRequest`. `hasRole("MODERATOR")` admits anyone granted `MODERATOR`; an `ADMIN` who is not also a moderator is not admitted.

Creating, updating, or deleting a POS requires `MODERATOR`. A plain `USER` is forbidden, while a
moderator succeeds:

```shell
# student2023 (USER) -> 403 Forbidden
curl -i --request POST -u student2023:ZwTwB8Hn8VkNLZec7bR1 --header "Content-Type: application/json" \
  --data '{"name":"New Cafe","description":"Demo","type":"CAFE","campus":"ALTSTADT","street":"Hauptstrasse","houseNumber":"100","postalCode":"69117","city":"Heidelberg"}' \
  http://localhost:8080/api/pos

# maxmustermann (MODERATOR) -> 201 Created
curl -i --request POST -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn --header "Content-Type: application/json" \
  --data '{"name":"New Cafe","description":"Demo","type":"CAFE","campus":"ALTSTADT","street":"Hauptstrasse","houseNumber":"100","postalCode":"69117","city":"Heidelberg"}' \
  http://localhost:8080/api/pos
```

Managing other users requires `ADMIN`. Any admin (`jane_doe` or `olivia_admin`) may change another user,
including their roles, or delete one:

```shell
# a moderator trying to edit another user (jane_doe) -> 403
curl -i --request PUT -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn --header "Content-Type: application/json" \
  --data '{"id":"ba419d35-0dfe-8af7-aee7-bbe10c45c028","loginName":"jane_doe","emailAddress":"jane.doe@uni-heidelberg.de","firstName":"Jane","lastName":"Doe","roles":["USER"]}' \
  http://localhost:8080/api/users/ba419d35-0dfe-8af7-aee7-bbe10c45c028

# the admin succeeds (this grants MODERATOR to student2023)
curl -i --request PUT -u jane_doe:aaaMbnPdFYDqkOpS3fVA --header "Content-Type: application/json" \
  --data '{"id":"aa616abe-1761-0c9a-e743-67bd738597dc","loginName":"student2023","emailAddress":"student2023@study.org","firstName":"Student","lastName":"Example","roles":["USER","MODERATOR"]}' \
  http://localhost:8080/api/users/aa616abe-1761-0c9a-e743-67bd738597dc
```

Any user may edit their *own* profile — name, email, and password; changing roles is an admin action. An
admin grants or revokes `MODERATOR` and `ADMIN`, but `USER` is the base role and is always retained — a
role set that omits it still keeps `USER`. Creating a user cannot grant a role either: a new account is
always a plain `USER`, regardless of any `roles` in the request body:

```shell
# even if the body asks for ADMIN, the new account is a plain USER
curl --request POST --header "Content-Type: application/json" \
  --data '{"loginName":"sneaky","emailAddress":"sneaky@uni-heidelberg.de","firstName":"S","lastName":"S","password":"sneaky-pass","roles":["ADMIN"]}' \
  http://localhost:8080/api/users
# -> 201, but GET shows roles = ["USER"]
```

Reading user data is not public either, because it exposes login names, emails, and roles. A plain `USER`
may read only their own account, and listing all users is admin-only:

```shell
# reading a user without credentials is rejected -> 401
curl -i http://localhost:8080/api/users/aa616abe-1761-0c9a-e743-67bd738597dc
```

A plain `USER` may read their own account, but not another's:

```shell
curl -i -u student2023:ZwTwB8Hn8VkNLZec7bR1 http://localhost:8080/api/users/aa616abe-1761-0c9a-e743-67bd738597dc   # own account -> 200
curl -i -u student2023:ZwTwB8Hn8VkNLZec7bR1 http://localhost:8080/api/users/ba419d35-0dfe-8af7-aee7-bbe10c45c028      # another user -> 403
```

Listing all users is admin-only:

```shell
curl -i -u student2023:ZwTwB8Hn8VkNLZec7bR1 http://localhost:8080/api/users   # a USER  -> 403
curl -i -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/api/users      # an admin -> 200
```

Editing or deleting a review needs either its *author* or a `MODERATOR` (an `ADMIN` is not a moderator
unless also granted `MODERATOR`). A plain `USER` editing or deleting someone else's review gets `403`;
a moderator gets `200`/`204`.

## 5. One approval per user

Approvals are attributed to the caller. The approve endpoint no longer takes a `user_id` query
parameter: before authentication the client passed it to name the approver, and now the approver is the
authenticated user, which the client cannot forge. A user cannot approve their own review, and cannot
approve the same review twice.

The review approved below (`947c82ee-1735-c9ed-c0a4-7deecc7229ce`) is the one `student2023` authored.

```shell
# self-approval is rejected -> 400 (student2023 is the author of this review)
curl -i --request PUT -u student2023:ZwTwB8Hn8VkNLZec7bR1 http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve
```

Three distinct non-author users approve it; the third reaches the quorum of 3:

```shell
curl -i --request PUT -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve       # 200
curl -i --request PUT -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve  # 200
curl -i --request PUT -u lisa_lee:lG6v9dGKZA5kfOHTFLNR http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve       # 200, this review is now approved
```

Any of them approving again is rejected as a duplicate:

```shell
# -> 409 Conflict
curl -i --request PUT -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve
```

A review becomes `approved` once it reaches the quorum (`campus-coffee.approval.min-count` = 3) of
distinct, non-author approvers. In the fixture data, `jane_doe`'s review of `Schmelzpunkt` is already
approved.

## 6. Stateless JWT bearer tokens

The same authorization rules also work with a bearer token. Log in once to get a short-lived JWT, then
send it as `Authorization: Bearer ...`:

```shell
# exchange credentials for a token, kept in $TOKEN
TOKEN=$(curl -s --request POST --header "Content-Type: application/json" \
  --data '{"loginName":"maxmustermann","password":"AmLtoD3r8lVdnwoLN1Nn"}' \
  http://localhost:8080/api/auth/token | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
```

Use the token in a follow-up write request; the same rules apply (here a moderator creates a POS):

```shell
curl -i --request POST --header "Authorization: Bearer $TOKEN" --header "Content-Type: application/json" \
  --data '{"name":"Token Cafe","description":"Demo","type":"CAFE","campus":"ALTSTADT","street":"Hauptstrasse","houseNumber":"1","postalCode":"69117","city":"Heidelberg"}' \
  http://localhost:8080/api/pos
```

The token expires after 15 minutes; after that, request a new one. A bearer token authenticates you as
the same user as Basic credentials do, so a `USER`'s token still cannot create a POS (`403`).

## 7. Second demo: deploy the solution to Google Cloud Run

The same walkthrough runs against a public deployment in the prod profile (`compose.prod.yaml`). In prod
the application enforces authentication, Swagger and the `/api/dev` endpoints are off, and the JWT secret
comes from the environment with no fallback. The prod profile loads the fixture data on startup, so the
demo has content without the dev endpoints. Cloud Run serves it over HTTPS, so the Basic credentials and
the JWT are encrypted in transit.

> The starter has no in-app authentication, so the README tells you to deploy it privately
> (`--no-allow-unauthenticated`) or to treat the deployment as throwaway. The solution instead grants
> public invocation (below), because app-level authentication is what makes a public deployment safe.

### Deploy

You need the `gcloud` CLI (provided via `mise.toml`), a Google Cloud project with billing enabled, and
the `beta` component. Set the project and a region up front so the deploy runs non-interactively:

```shell
gcloud components install beta
gcloud auth login
gcloud config set project <your-project-id>
gcloud config set run/region <region>   # e.g. europe-west3
```

Deploy from source with **one command**. [`scripts/deploy-cloudrun.sh`](scripts/deploy-cloudrun.sh)
generates a gitignored `deploy.env` with a random JWT secret and runs `gcloud beta run compose up
--allow-unauthenticated`, which builds the image and creates **one** Cloud Run service (named after the
Compose project, `campus-coffee-prod`) that runs the app and PostgreSQL as **sidecar containers** sharing
one network namespace — which is why `compose.prod.yaml` reaches the database at `localhost`, not the
Compose service name `db`:

```shell
scripts/deploy-cloudrun.sh                  # event sourcing mode
scripts/deploy-cloudrun.sh relational       # relational mode
```

`compose up` has no flag to set environment variables, so the JWT secret (and the persistence mode) reach
the prod profile — which has no fallback secret — through the Compose file's `env_file: deploy.env` (not
`${JWT_SECRET}` interpolation, which `compose up` does not read from your shell). `--allow-unauthenticated`
grants public invocation in the same command, so the app's own authentication (not Cloud Run's IAM layer)
gates write requests. The service comes up healthy in one step; a redeploy is the same single command, and
the secret in `deploy.env` is reused so JWTs issued earlier keep working.

To deploy by hand instead of via the script, create `deploy.env` from the template and run the same
`compose up`:

```shell
cp deploy.env.example deploy.env      # then set JWT_SECRET, e.g. to the output of `openssl rand -hex 32`
gcloud beta run compose up compose.prod.yaml --allow-unauthenticated
```

#### Event-sourcing vs. relational mode

The two modes differ only in `CAMPUS_COFFEE_PERSISTENCE_MODE` in `deploy.env` (the script sets it from its
argument; see
[Inspecting the event sourcing persistence mode](#inspecting-the-event-sourcing-persistence-mode)). In
event sourcing mode the prod fixture load writes through the event log, so the `events` table is already
populated (`5 users, 4 POS, 3 reviews`) and the relational tables are projected from it. The API behaves
identically, and a write request you make over HTTPS appends a new event. (Because the sidecar database is
ephemeral, each cold start replays the fixture load through the log, so the `events` table is rebuilt per
instance.)

Read the service URL into a variable, with `/api` appended for the API base path:

```shell
export BASE=$(gcloud run services describe campus-coffee-prod --format='value(status.url)')/api
```

### Run the same demo over HTTPS

The prod profile loads the fixture data on startup, so go straight to the calls. Set `$BASE` first (the
block above); in a fresh shell it is unset, and `curl` against an empty `$BASE` fails. It must end in
`/api`, and every endpoint lives under it: a request to the bare service host (without `/api`) returns
`404 No endpoint found`. Run the blocks one at a time; they are reads or are rejected before anything is
written, so the demo re-runs cleanly against the fixture data without creating duplicates.

A read request is public:

```shell
curl $BASE/pos
```

A write request needs authentication. An unauthenticated `POST` is `401`, and nothing is created:

```shell
curl -i --request POST --header "Content-Type: application/json" \
  --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"Hello from the cloud"}' $BASE/reviews
```

Listing users is `ADMIN`-only. The same endpoint under three identities walks the whole auth ladder
without writing anything:

```shell
curl -i $BASE/users                                       # unauthenticated -> 401
curl -i -u student2023:ZwTwB8Hn8VkNLZec7bR1 $BASE/users   # a USER          -> 403
curl -i -u jane_doe:aaaMbnPdFYDqkOpS3fVA  $BASE/users     # an ADMIN        -> 200 (the user list, with emails)
```

Curating a POS needs `MODERATOR`; a plain `USER` is rejected before anything is created:

```shell
curl -i --request POST -u student2023:ZwTwB8Hn8VkNLZec7bR1 --header "Content-Type: application/json" \
  --data '{"name":"Cloud Cafe","description":"Demo","type":"CAFE","campus":"ALTSTADT","street":"Hauptstrasse","houseNumber":"100","postalCode":"69117","city":"Heidelberg"}' \
  $BASE/pos                                               # USER -> 403
```

A bearer token carries the same authorities as Basic credentials. Log in once and keep the token in an
environment variable; it persists across the blocks below in the same shell:

```shell
export TOKEN=$(curl -s --request POST --header "Content-Type: application/json" \
  --data '{"loginName":"jane_doe","password":"aaaMbnPdFYDqkOpS3fVA"}' \
  $BASE/auth/token | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
echo "$TOKEN"   # the JWT: header.payload.signature
```

The payload (the middle segment) carries the authorization data. Decode it to see how roles are encoded:
the `roles` claim holds the bare role names, which the `JwtAuthenticationConverter` maps to `ROLE_*`
authorities on the way in:

```shell
echo "$TOKEN" | cut -d. -f2 | python3 -c 'import sys,base64,json; p=sys.stdin.read().strip(); p+="="*(-len(p)%4); print(json.dumps(json.loads(base64.urlsafe_b64decode(p)),indent=2))'
# -> {"sub":"jane_doe","exp":...,"iat":...,"roles":["ADMIN","MODERATOR","USER"]}
```

Reuse the token in a follow-up request. `jane_doe` is an `ADMIN`, so the listing is `200` under Bearer too:

```shell
curl -i --header "Authorization: Bearer $TOKEN" $BASE/users
```

A `USER`'s token is exactly as limited as their Basic credentials: redo the two token blocks with
`student2023:ZwTwB8Hn8VkNLZec7bR1`, and the same `GET $BASE/users` returns `403`. The token expires after
15 minutes; request a new one after that. A successful write request (for example `POST $BASE/reviews` with
credentials) returns `201` and is authored by the caller, but it inserts a row, so repeating it for the
same author and POS returns `409`. The prod database is ephemeral and reloads the fixtures on a cold start.
Sending credentials over the public URL is safe only because Cloud Run terminates TLS.

### Notes for a real deployment

This is a throwaway demo; a real deployment differs in a few ways:

- **Database.** `compose.prod.yaml` runs PostgreSQL as a sidecar container sharing the app's network
  namespace (reached at `localhost`), which Cloud Run treats as ephemeral: a cold start brings up an empty
  database, and the startup loader reloads the fixture data. That suits a demo but keeps nothing. For
  persistence, point the app at a managed database such as Cloud SQL and set
  `campus-coffee.fixtures.load-on-startup` to `false`.
- **Secret.** The prod profile has no fallback `JWT_SECRET`, so a deploy that forgets to set it fails at
  startup rather than booting with a known key. Supply it from Secret Manager (a Cloud Run secret
  reference), not the `gcloud run services update --update-env-vars` shown above, which is fine for a demo
  but writes the secret into the service's plain environment.
- **Exposure.** The prod profile keeps Swagger and the `/api/dev` endpoints off, and Cloud Run serves the
  app over HTTPS, so the Basic credentials and the JWT are encrypted in transit.

### Tear it down

Delete the deployment when you are done:

```shell
gcloud run services delete campus-coffee-prod
```

## Inspecting the event sourcing persistence mode

The application runs in an event-first **event sourcing** mode by default (`campus-coffee.persistence.mode`),
where an append-only event log is the source of truth and the relational tables are a read model projected
from it. The API behaves exactly the same; this step shows the events that each write request records.

A default run is already in event sourcing mode (a local `docker compose up`, or a `bootRun` with no extra
flag; pass `--campus-coffee.persistence.mode=relational` to opt out). The fixture load on startup writes
through the event log, so the `events` table is already populated:

```shell
# every fixture row, plus any write request you make, is recorded as an event (seq is the append order)
docker exec -it db psql -U postgres -c \
  "SELECT seq, change_type, entity_type FROM events ORDER BY seq;"
```

Make a write request (e.g., approve a review as in step 5) and re-run the query: a new `INSERT`/`UPDATE`
event appears, while the `reviews` table still serves the read request. The ids and timestamps in the
`body` column match the rows, because the read model is projected from exactly these events. The seeded
entity ids are unchanged from the relational mode (the event ids come from a separate generator).

## Observability with Spring Boot Actuator

Spring Boot Actuator exposes operational endpoints under `/actuator` (its own base path, separate from the
`/api` prefix the controllers use). Which endpoints are reachable is configured in `application.yaml`
(`management.endpoints.web.exposure.include: health, metrics, env`; the prod profile drops `env`), and access
is enforced in `SecurityConfig` with the same role rules as the rest of the API: **health is public**, while
**metrics requires an ADMIN**.

### Health

The health endpoint reports an overall `UP`/`DOWN` status, with details hidden by default, so it is safe to
expose without authentication (a load balancer or Cloud Run probes it):

```shell
curl http://localhost:8080/actuator/health
# -> {"groups":["liveness","readiness"],"status":"UP"}
```

### Metrics (ADMIN only)

The metrics endpoint is gated on the ADMIN role, so an unauthenticated request is rejected:

```shell
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/metrics
# -> 401
```

As an admin (jane_doe), it lists the available meters:

```shell
curl -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/actuator/metrics
# -> {"names":["application.ready.time","disk.free","hikaricp.connections.active",
#     "http.server.requests","jvm.memory.used","jdbc.connections.active", ...]}
```

Append a meter name to read its current value. JVM memory in use, tagged by area (heap/non-heap):

```shell
curl -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/actuator/metrics/jvm.memory.used
# -> {"name":"jvm.memory.used","measurements":[{"statistic":"VALUE","value":236862112.0}],
#     "baseUnit":"bytes","availableTags":[{"tag":"area","values":["heap","nonheap"]}, ...]}
```

HTTP request count and latency, recorded per endpoint (the COUNT grows as you exercise the API). Filter with
a tag to focus on one route or status:

```shell
curl -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/actuator/metrics/http.server.requests
# -> measurements: COUNT, TOTAL_TIME, MAX; availableTags: uri, status, method, outcome, exception

curl -u jane_doe:aaaMbnPdFYDqkOpS3fVA \
  "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/api/pos&tag=status:200"
# -> the same meter, narrowed to successful requests to /api/pos
```

`management.metrics.enable.all: true` keeps every available meter on. A real deployment would scrape these
(for example via a Prometheus endpoint) into a dashboard rather than read them by hand.

## Reset the local demo

The dev app loads the fixture data on startup, and `PUT /api/dev/data` clears and reloads it, reassigning
the same seeded ids, so you can rerun the local walkthrough from a clean state. The Cloud Run deployment
loads its data on startup, so redeploy it to reset.
