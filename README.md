# CampusCoffee

A Spring Boot teaching application for managing points of sale (POS) — cafés, bakeries, vending machines, and the like — on campus, with users and reviews. It follows a hexagonal (ports-and-adapters) architecture enforced by ArchUnit, built with Gradle (Kotlin DSL) on Java 25.

## Authentication and authorization

Reads of the POS directory and of reviews are public. Every write request needs authentication, and user
data (login names, emails, roles) is readable only by that user or an admin. An unauthenticated write
request is rejected with `401`; an authenticated caller who lacks the required role gets `403`.

The fixture users and their passwords are listed under [Dev endpoints](#dev-endpoints-apidev), and
`INSTRUCTOR.md` has a full walkthrough.

### Authenticating

Authenticate either with HTTP Basic, sending the credentials on each request:

```shell
curl -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/api/users
```

or by exchanging credentials once for a stateless JWT (valid 15 minutes) and sending it as a bearer token:

```shell
TOKEN=$(curl -s --request POST --header "Content-Type: application/json" \
  --data '{"loginName":"maxmustermann","password":"AmLtoD3r8lVdnwoLN1Nn"}' \
  http://localhost:8080/api/auth/token | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
```

```shell
curl --header "Authorization: Bearer $TOKEN" http://localhost:8080/api/pos
```

A bearer token authenticates the same principal as the matching Basic credentials, so the authorization
rules below hold identically under either mechanism.

### Roles and access rules

`USER` is the base role and is always retained. An admin cannot strip it, and a new account is always a
plain `USER` regardless of any `roles` in the registration body. `MODERATOR` (content moderation) and
`ADMIN` (user administration) are independent grants on top. The coarse rules based on the request path live
in `SecurityConfig`; the finer rules (e.g., a review's author, or a user acting on their own account) are enforced in
the domain services, because they depend on which row is targeted.

| Request | Allowed for |
|---|---|
| `GET /api/pos`, `GET /api/reviews` (and their reads) | anyone (public) |
| `POST /api/users` (registration), `POST /api/auth/token` | anyone (public) |
| `GET /api/users` (list all) | `ADMIN` |
| `GET /api/users/{id}` | the user themselves or `ADMIN` |
| `POST` / `PUT` / `DELETE /api/pos` (and the OSM import) | `MODERATOR` |
| `PUT /api/users/{id}` | the user themselves; changing roles needs `ADMIN` |
| `DELETE /api/users/{id}` | `ADMIN` |
| `POST /api/reviews` | any authenticated user (authored as the caller) |
| `PUT` / `DELETE /api/reviews/{id}` | the review's author or a `MODERATOR` |
| `PUT /api/reviews/{id}/approve` | any authenticated user except the author |
| `/actuator/health` | anyone (public) |
| `/actuator/metrics`, `/actuator/env` | `ADMIN` |

`hasRole("MODERATOR")` admits anyone granted `MODERATOR`; an admin who is not also a moderator is not
admitted to the moderator-only routes.

## Prerequisites

* Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) or a compatible open-source alternative such as [Rancher Desktop](https://rancherdesktop.io/).
* Install the [Temurin JDK 25](https://adoptium.net/temurin/releases/?version=25&os=any&arch=any) and [Gradle 9.5](https://gradle.org/install/) either via the provided [`mise.toml`](mise.toml) file (see [getting started guide](https://mise.jdx.dev/getting-started.html) for details) or directly via your favorite package manager. If you use `mise`, run `mise trust mise.toml` and then `mise install` in the project root to set up the required tool versions. There is no Gradle wrapper; run Gradle through `mise`.
* Install an IDE with Kotlin support. We recommend [IntelliJ](https://www.jetbrains.com/idea/), but you are free to use alternatives such as [VS Code](https://code.visualstudio.com/) with suitable extensions.
* Import the project into your IDE. In IntelliJ, you can do this via `File` -> `Open` and selecting the project's root folder (the multi-module build is defined by the root-level `settings.gradle.kts`; there is no root `build.gradle.kts` because the shared build logic lives in `build-logic/`). If you have the `mise` [plugin](https://plugins.jetbrains.com/plugin/24904-mise) installed, IntelliJ will ask you to select the appropriate tool versions.
* Ensure that your IDE as initialized the project correctly, including all `src`, `test`, and `resources` folders.

## Build application

First, make sure that the Docker daemon is running (the tests use Testcontainers).
Then, to build the application, run the following command in the command line (or use the Gradle integration of your IDE):

```shell
gradle build
```
**Note:** In the `dev` profile the application loads the fixture dataset on startup; the [Dev endpoints](#dev-endpoints-apidev) let you inspect, reload, or clear it.

You can use the quiet mode to suppress most log messages:

```shell
gradle build -q
```

The Kotlin code is formatted and linted with [ktlint](https://pinterest.github.io/ktlint/). `gradle build`
fails on formatting violations (the `ktlintCheck` task runs as part of `check`); apply the fixes with:

```shell
gradle ktlintFormat
```

Static analysis runs with [detekt](https://detekt.dev/), also wired into `check`, so `gradle build`
fails on findings too. A per-module `detekt-baseline.xml` grandfathers the existing findings (regenerate
it with `gradle detektBaseline`).

## Start application

First, make sure that the Docker daemon is running.
Before you start the application, you first need to start a Postgres docker container:

```shell
docker run -d --name db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18-alpine
```

Then, you can start the application in the `dev` profile for local development:

```shell
gradle :application:bootRun --args='--spring.profiles.active=dev'
```
**Note:** The data source is configured via the [`application.yaml`](application/src/main/resources/application.yaml) file.

In the `dev` profile the application loads the fixture dataset on startup, so the examples below have data
to work with; the [Dev endpoints](#dev-endpoints-apidev) let you reload or clear it.

### Persistence mode (event sourcing by default)

By default, the application runs in an event-first **event sourcing** mode, where an append-only event log
is the source of truth and the relational tables are a read model projected from it. The API behaves
identically to the plain relational mode; the difference is that every write request is also recorded in
the `events` table. A default `dev` run therefore already records events:

```shell
gradle :application:bootRun --args='--spring.profiles.active=dev'
```

It can also run in a plain **relational** mode that writes straight to the tables, with no event log:

```shell
gradle :application:bootRun --args='--spring.profiles.active=dev --campus-coffee.persistence.mode=relational'
```

Inspect the log (its `seq` column is the append order):

```sql
SELECT seq, change_type, entity_type FROM events ORDER BY seq;
```

To import an existing relational database into the log and then rebuild the tables from it, run these once
each (they are one-off startup migrations, off by default; both act only in event sourcing mode, the
default):

```shell
# 1. seed the log from the current rows (one INSERT event per row)
gradle :application:bootRun --args='--spring.profiles.active=dev --campus-coffee.persistence.data-to-events-on-startup=true'

# 2. rebuild the tables from the log (clears the tables, then replays every event, preserving ids and timestamps)
gradle :application:bootRun --args='--spring.profiles.active=dev --campus-coffee.persistence.events-to-data-on-startup=true'
```

## Explore the REST API

### OpenAPI specification

After starting the application in the `dev` profile, you can access the OpenAPI specification (JSON) at [`http://localhost:8080/api/api-docs`](http://localhost:8080/api/api-docs).<br/>
You can also access the Swagger UI to interactively explore the API at [`http://localhost:8080/api/swagger-ui.html`](http://localhost:8080/api/swagger-ui.html).

### Local testing

You can use `curl` in the command line to send HTTP requests to the REST API.

#### Dev endpoints (/api/dev)

In the `dev` profile the application loads the fixture dataset on startup (when the database has no users
yet), and the database persists across application restarts. Three endpoints let you inspect, replace, and
clear the data on demand. They need no credentials, for convenient local testing, and they are registered
only in the `dev` profile (which must never run on a network-reachable host):

Report the current counts (`{users, pos, reviews}`):

```shell
curl http://localhost:8080/api/dev/data
```

Replace the data with the fixture dataset (idempotent: clears first, safe to repeat):

```shell
curl --request PUT http://localhost:8080/api/dev/data
```

Clear all data:

```shell
curl --request DELETE http://localhost:8080/api/dev/data
```

The fixture dataset includes five users with known passwords. `USER` is the base and is always held (an
admin cannot strip it); `MODERATOR` (content moderation) and `ADMIN` (user administration) are independent
grants on top, so a user holds whichever capabilities they were given. For example, `jane_doe` holds all three, while `olivia_admin` is an admin who
is not a moderator. Passwords are stored only as hashes and are never returned in any response.

| Login name      | Password               | Roles                        |
|-----------------|------------------------|------------------------------|
| `jane_doe`      | `aaaMbnPdFYDqkOpS3fVA` | `USER`, `MODERATOR`, `ADMIN` |
| `maxmustermann` | `AmLtoD3r8lVdnwoLN1Nn` | `USER`, `MODERATOR`          |
| `student2023`   | `ZwTwB8Hn8VkNLZec7bR1` | `USER`                       |
| `lisa_lee`      | `lG6v9dGKZA5kfOHTFLNR` | `USER`                       |
| `olivia_admin`  | `Qp7r2sV9xKmN4bLdTtYw` | `USER`, `ADMIN`              |

The fixture data records `review_approvals` rows consistent with each review's approval count. `jane_doe`'s
review of `Schmelzpunkt` reaches the quorum, so it starts out approved. Write requests require authentication,
so pass one of these credentials, e.g. `-u jane_doe:aaaMbnPdFYDqkOpS3fVA`.

Resource ids are `UUID`s the server assigns on creation. With the default seed (`campus-coffee.id.entity-seed` =
`42`), a freshly loaded fixture dataset always gets the same ids, so the examples below use the concrete
fixture ids, each with a `#` comment naming the entity. If you have changed the data, read the current id
from a list or filter response (e.g., `GET /api/pos` or `GET /api/users/filter?login_name=jane_doe`).

#### POS endpoints (/api/pos)

**Get POS:**

All POS:
```shell
curl http://localhost:8080/api/pos
```

POS by ID:
```shell
curl http://localhost:8080/api/pos/eb5910f1-26e6-bc6f-6fbd-df557096b883 # Schmelzpunkt
```

POS by name:
```shell
curl 'http://localhost:8080/api/pos/filter?name=Schmelzpunkt' # add valid POS name here; quote the URL so zsh does not glob the ?
```

##### Create POS

Create a POS based on a JSON object provided in the request body:

```shell
curl --request POST -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn --header "Content-Type: application/json" --data '{"name":"New Café","description":"Description","type":"CAFE","campus":"ALTSTADT","street":"Hauptstraße","houseNumber":"100","postalCode":"69117","city":"Heidelberg"}' http://localhost:8080/api/pos
```

Create a POS based on an OpenStreetMap node:

```shell
curl --request POST -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn 'http://localhost:8080/api/pos/import/osm/5589879349?campus_type=ALTSTADT' # set a valid OSM node ID here
```

IDs for testing:
* 5589879349 (Rada Coffee & Rösterei in ALTSTADT)
* 1864600258 (La Fée in ALTSTADT)
* 1864600236 (Café Moro in ALTSTADT) --> missing address

See bean validation in action:

```shell
curl --header "Content-Type: application/json" --request POST -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn -i --data '{"name":"","description":"","type":"CAFE","campus":"ALTSTADT","street":"Hauptstraße","houseNumber":"100","postalCode":"69117","city":"Heidelberg"}' http://localhost:8080/api/pos
```

##### Update POS

Update title and description:
```shell
curl --header "Content-Type: application/json" --request PUT -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn --data '{"id":"eb5910f1-26e6-bc6f-6fbd-df557096b883","name":"New coffee","description":"Great croissants","type":"CAFE","campus":"ALTSTADT","street":"Hauptstraße","houseNumber":"95","postalCode":"69117","city":"Heidelberg"}' http://localhost:8080/api/pos/eb5910f1-26e6-bc6f-6fbd-df557096b883 # Schmelzpunkt; the path and body id must match
```

##### Delete POS

Delete POS by ID:
```shell
curl --request DELETE -u maxmustermann:AmLtoD3r8lVdnwoLN1Nn -i http://localhost:8080/api/pos/bff9d9d5-ee3d-d852-62f6-0bdbcc5c8305 # Bäcker Görtz (no reviews)
```

**Note:** A POS that still has reviews cannot be deleted; the API answers `409 Conflict`. With the
fixture data, `Schmelzpunkt` has reviews. Delete its reviews first or pick a POS without reviews (e.g.,
`Bäcker Görtz` or `Café Botanik`).

#### Users endpoints (/api/users)

##### Get users

All users:
```shell
curl http://localhost:8080/api/users
```

User by ID:
```shell
curl http://localhost:8080/api/users/ba419d35-0dfe-8af7-aee7-bbe10c45c028 # jane_doe
```

User by login name:
```shell
curl 'http://localhost:8080/api/users/filter?login_name=jane_doe' # add valid user login name here
```

##### Create users

```shell
curl --header "Content-Type: application/json" --request POST --data '{"loginName":"other_login_name","emailAddress":"other.person@uni-heidelberg.de","firstName":"New","lastName":"Person","password":"demo-password"}' http://localhost:8080/api/users
```

See bean validation in action:
```shell
curl --header "Content-Type: application/json" --request POST -i --data '{"loginName":"other_login_name!","emailAddress":"other.personATuni-heidelberg.de","firstName":"","lastName":""}' http://localhost:8080/api/users
```

##### Update user

Update the login name and the email address:
```shell
curl --header "Content-Type: application/json" --request PUT -u jane_doe:aaaMbnPdFYDqkOpS3fVA --data '{"id":"ba419d35-0dfe-8af7-aee7-bbe10c45c028","createdAt":"2025-06-03T12:00:00","updatedAt":"2025-06-03T12:00:00","loginName":"jane_doe_new","emailAddress":"jane.doe.new@uni-heidelberg.de","firstName":"Jane","lastName":"Doe"}' http://localhost:8080/api/users/ba419d35-0dfe-8af7-aee7-bbe10c45c028 # jane_doe; the path and body id must match
```

##### Delete user

Delete user by ID:
```shell
curl --request DELETE -u jane_doe:aaaMbnPdFYDqkOpS3fVA -i http://localhost:8080/api/users/5e688e99-61b3-5c88-4697-6cf7b0bfbe20 # lisa_lee (no reviews)
```

**Note:** A user who still has reviews cannot be deleted; the API answers `409 Conflict`. With the
fixture data, `jane_doe`, `maxmustermann`, and `student2023` have authored reviews. Delete their reviews
first or create a fresh user.

#### Reviews endpoint (/api/reviews)

##### Get reviews

All reviews:
```shell
curl http://localhost:8080/api/reviews
```

Review by ID:
```shell
curl http://localhost:8080/api/reviews/2c167999-289d-95fa-9661-a43246302cd9 # jane_doe's review of Schmelzpunkt
```

Get approved reviews for a POS:
```shell
curl 'http://localhost:8080/api/reviews/filter?pos_id=eb5910f1-26e6-bc6f-6fbd-df557096b883&approved=true' # Schmelzpunkt; quote the URL so the shell does not treat & as a job control operator
```

##### Create reviews

The author is the authenticated user, so a create needs Basic auth and the body carries no `authorId`:
```shell
curl --header "Content-Type: application/json" --request POST -u student2023:ZwTwB8Hn8VkNLZec7bR1 --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"Great place to study."}' http://localhost:8080/api/reviews # Café Botanik
```

A user cannot review the same POS twice (the second request returns `409 Conflict`):
```shell
curl --header "Content-Type: application/json" --request POST -u student2023:ZwTwB8Hn8VkNLZec7bR1 --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"Great place to study."}' http://localhost:8080/api/reviews # Café Botanik
```

##### Approve reviews

The approver is the authenticated user (there is no `user_id` parameter), and a user cannot approve their
own review. The review below is the one `student2023` authored (of `New Vending Machine`):
```shell
curl -i --request PUT -u student2023:ZwTwB8Hn8VkNLZec7bR1 http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve # student2023 is the author, so this returns 400
```

Another user can approve it, but only once. The first approval succeeds:
```shell
curl -i --request PUT -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve # 200
```

The same user approving again is rejected as a duplicate:
```shell
curl -i --request PUT -u jane_doe:aaaMbnPdFYDqkOpS3fVA http://localhost:8080/api/reviews/947c82ee-1735-c9ed-c0a4-7deecc7229ce/approve # 409 Conflict
```

## Docker

### Building an image from the Dockerfile

```shell
docker build -t campus-coffee:latest .
```

#### Manually create and run a Docker container based on the created image

First, create a new Docker network named `campus-coffee-net`,
then run a Postgres container and connect it to `campus-coffee-net`.
Finally, run the container with the application (in `dev` profile, do not use in production),
connect it to the network, and configure the application
to use the database provided in the started Postgres container.

```shell
docker network create campus-coffee-net 2>/dev/null || true
docker rm -f db 2>/dev/null || true
docker run -d --name db --net campus-coffee-net -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18-alpine
docker run --net campus-coffee-net -e SPRING_PROFILES_ACTIVE=dev -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/postgres -p 8080:8080  -it --rm campus-coffee:latest
```

Explanation of selected options:

`docker run -p 8080:8080 ` runs the container with port 8080 exposed to the host machine. You can change the port mapping if needed.
`docker run ... -it`  runs a container in interactive mode with a pseudo-TTY (terminal).
`docker run ... --rm` automatically removes the container (and its associated resources) if it exists already.

Both run methods start the app in the `dev` profile, which loads the fixture dataset on startup when the database has no users yet, so the API comes up populated; the [Dev endpoints](#dev-endpoints-apidev) let you inspect, reload, or clear it.

#### Use Docker compose to run the app container together with the DB container

Build container image:

```shell
docker compose build
```

Delete the existing DB container (if you manually created it before):

```shell
docker rm -f db 2>/dev/null || true
```

Create and start containers (the Compose file defaults `DB_HOST` to `localhost` for Cloud Run, so set
`DB_HOST=db` locally to reach the database container by its Compose service name):

```shell
docker compose down && DB_HOST=db docker compose up
```

Stop and remove containers and networks:

```shell
docker compose down
```

The `db` service has no named volume, so `docker compose down` discards its data; the next `DB_HOST=db docker compose up` starts with an empty database, which the dev-profile startup loader then reseeds with the fixture dataset (the [Dev endpoints](#dev-endpoints-apidev) let you reload or clear it on demand).

## Deployment

### Deploy CampusCoffee to Google Cloud Run

We use the `gcloud` CLI (see [`mise.toml`](mise.toml)) to build CampusCoffee from source with Cloud Build
and deploy it to Cloud Run. We deploy the **prod profile** via [`compose.prod.yaml`](compose.prod.yaml):
authentication is enforced, Swagger and the `/api/dev` endpoints are off, and the JWT secret comes from
the environment (no insecure fallback). Prod does not seed the fixtures by default (the seeded users carry
committed passwords); set `LOAD_FIXTURES_ON_STARTUP=true` in `deploy.env` so this throwaway demo has
content without the dev endpoints. Cloud Run terminates TLS, so the Basic credentials and the JWT are
encrypted in transit. App-level authentication is what makes a public URL safe.

Deploying from Compose is [still in preview](https://docs.cloud.google.com/run/docs/deploy-run-compose),
so install the `beta` component first (`gcloud` prompts to install any other required components on first
run). You also need a Google Cloud project with billing enabled.

```shell
gcloud components install beta
gcloud auth login
gcloud config set project <your-project-id>
gcloud config set run/region <region>   # e.g. europe-west3; otherwise compose up prompts for one
```

Deploy from source with **one command**. [`scripts/deploy-cloudrun.sh`](scripts/deploy-cloudrun.sh)
generates a gitignored `deploy.env` with a random JWT secret and runs `gcloud beta run compose up`, which
builds the image and creates **one** Cloud Run service (named after the Compose project,
`campus-coffee-prod`) that runs the app and PostgreSQL as **sidecar containers** sharing one network
namespace (which is why `compose.prod.yaml` reaches the database at `localhost`):

```shell
scripts/deploy-cloudrun.sh                   # event sourcing mode (the default)
#scripts/deploy-cloudrun.sh relational        # relational mode (pick one)
```

Before deploying, the script prints the resolved target — service, project, region, and persistence mode —
and asks for confirmation, so a stale `gcloud config` cannot send the deploy to the wrong project. Pass
`--project <id>` to target a project other than the active config, and `-y` (or `--yes`) to skip the prompt
for a non-interactive run.

`compose up` has no flag to set environment variables, so the JWT secret (and the persistence mode) reach
the prod profile — which has no fallback secret — through the Compose file's `env_file: deploy.env`. The
script writes `deploy.env`, and `--allow-unauthenticated` grants public invocation in the same command, so
the **app's own** authentication (not Cloud Run's IAM layer) gates write requests. The service comes up
healthy in one step, and a redeploy is the same single command — the secret in `deploy.env` is reused, so
JWTs issued earlier keep working.

To deploy by hand instead of via the script, create `deploy.env` from the template and run the same
`compose up`:

```shell
cp deploy.env.example deploy.env      # then set JWT_SECRET, e.g. to the output of `openssl rand -hex 32`
gcloud beta run compose up compose.prod.yaml --allow-unauthenticated
```

The two modes differ only in `CAMPUS_COFFEE_PERSISTENCE_MODE` in `deploy.env` (the script sets it from its
argument). In event sourcing mode the prod fixture load writes through the event log, so the `events` table
is populated and the relational tables are projected from it; the API behaves identically.

Read the service URL (with `/api` appended for the API base path) and exercise it:

```shell
export BASE=$(gcloud run services describe campus-coffee-prod --format='value(status.url)')/api
```

A public read succeeds:

```shell
curl "$BASE/pos"                                                              # public read -> 200
```

An unauthenticated write request is rejected, and nothing is created:

```shell
curl -i --request POST --header "Content-Type: application/json" \
  --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"Hello from the cloud"}' "$BASE/reviews"        # no creds -> 401
```

The same write request with valid credentials succeeds:

```shell
curl -i --request POST -u student2023:ZwTwB8Hn8VkNLZec7bR1 \
  --header "Content-Type: application/json" \
  --data '{"posId":"2d68ad16-268a-478c-9827-50f4569b5949","review":"Hello from the cloud"}' "$BASE/reviews"        # with creds -> 201
```

Every call from the [Authentication and authorization](#authentication-and-authorization) section works
the same way against `$BASE`, including the role checks and the JWT flow (`POST $BASE/auth/token`).

> **The first request after idle is slow (a cold start).** With no minimum instances configured the service
> scales to zero when idle, so the first request boots a fresh instance — and because the app and PostgreSQL
> run as sidecars, that means starting Postgres, then the app (Flyway, the event-sourcing startup runners,
> and the fixture load) before it serves, which takes roughly 20–60s. Cloud Run holds the request while the
> instance starts (the startup probe allows 240s and the request timeout is 300s), but a client with a
> shorter timeout may see the first call hang or fail and then succeed once an instance is warm. Hit
> `/actuator/health` once to warm it before a demo, or set `--min-instances=1`
> (`gcloud run services update campus-coffee-prod --min-instances=1`) to keep one instance warm — at the
> cost of an always-on billed instance.

This is a throwaway demo. Running PostgreSQL as a sidecar container next to the app is fine for a demo but
not how you would run it in production: Cloud Run treats the container as ephemeral, so a cold start
brings up an empty database that the startup loader reseeds only when the demo sets
`LOAD_FIXTURES_ON_STARTUP=true`. A real cloud deployment points the app at a **managed (hosted) database**
— e.g., Cloud SQL — instead of a self-managed Postgres container, leaves
`campus-coffee.fixtures.load-on-startup` at its prod default (`false`), and supplies `JWT_SECRET` from
Secret Manager rather than a generated value. Delete the deployment when you are done:

```shell
gcloud run services delete campus-coffee-prod
```

## Code coverage and mutation testing

### Code coverage (JaCoCo)

Coverage is measured with JaCoCo. Most production code in `domain`, `api`, and `data` is tested
by the system and acceptance tests in the `application` module, so per-module reports alone are
misleading. The `coverage` module aggregates the execution data from every module into a single
report that covers all of them together.

Run the full build to produce the reports:

```shell
gradle build
```

- Combined report: [`coverage/build/reports/jacoco/testCodeCoverageReport/html/index.html`](coverage/build/reports/jacoco/testCodeCoverageReport/html/index.html)
- Per-module reports: `domain/build/reports/jacoco/test/html/index.html`, `api/...`, `data/...`

`gradle build` also enforces the coverage gate (the `coverageGate` task, wired into `check`): the build
fails when the aggregated line or branch coverage is below the minimums configured in
[`coverage/build.gradle.kts`](coverage/build.gradle.kts). The minimums are set to the current measured
coverage; raise them as you add tests so the bar follows the suite. The CI workflow runs `gradle build`
and uploads the reports as the `jacoco-coverage-reports` artifact, so you can browse the uncovered lines
without a local run.

### Mutation testing (PITest)

Mutation testing reports whether the tests actually detect changed behavior. It is opt-in via the
`-Pmutation` property and meant to be run locally, since it re-runs the tests for every mutant and the
data and system tests run against a PostgreSQL database in a container managed by Testcontainers. Each
module runs PITest against its own tests and writes its own report: `domain` mutates `domain.*`, `api`
mutates `api.*`, and `data` mutates `data.*`, each against that module's own unit and integration tests;
the `application` module additionally mutates `api.*` and `data.*` against the system and acceptance
tests via additional mutable code paths (the Gradle equivalent of Maven's `crossModule`). The api and
data classes therefore appear in two reports (the module's own and application's), which are not merged.
Read a module's report for what its own tests catch and the application report for what the system tests
catch (the controllers, for example, have no api-local tests and are killed only there). The generated
`*MapperImpl` classes are excluded from mutation, mirroring the JaCoCo gate.

```shell
# Full run across all modules.
gradle pitest -Pmutation

# Stronger or exhaustive mutator groups produce more, harder-to-kill mutants:
gradle pitest -Pmutation -Ppitest.mutators=STRONGER
gradle pitest -Pmutation -Ppitest.mutators=ALL

# Scope to one module while iterating (e.g., only domain, skipping the slow Testcontainers modules):
gradle :domain:pitest -Pmutation
```

Reports are written per module at `<module>/build/reports/pitest/index.html` (`domain`, `api`, `data`, and
`application`).

Surviving mutants point to behavior the tests run but do not assert; add assertions until they are
killed.

### Growing the test suite

The reports are a worklist for new tests:

1. Open the aggregate coverage report and pick an uncovered package or class.
2. Add tests for the uncovered lines and branches.
3. Run PITest on that class and add assertions until the surviving mutants are killed.
4. Raise the coverage minimums in `coverage/build.gradle.kts` to the new measured level.
