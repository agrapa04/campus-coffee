# CampusCoffee

A Spring Boot teaching application for managing points of sale (POS) — cafés, bakeries, vending machines, and the like — on campus, with users and reviews. It follows a hexagonal (ports-and-adapters) architecture enforced by ArchUnit, built with Gradle (Kotlin DSL) on Java 25.

## Prerequisites

* Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) or a compatible open-source alternative such as [Rancher Desktop](https://rancherdesktop.io/).
* Install the [Temurin JDK 25](https://adoptium.net/temurin/releases/?version=25&os=any&arch=any) and [Gradle 9.5](https://gradle.org/install/) either via the provided [`mise.toml`](mise.toml) file (see [getting started guide](https://mise.jdx.dev/getting-started.html) for details) or directly via your favorite package manager. If you use `mise`, run `mise trust mise.toml` and then `mise install` in the project root to set up the required tool versions. There is no Gradle wrapper; run Gradle through `mise`.
* Install an IDE with Kotlin support. We recommend [IntelliJ](https://www.jetbrains.com/idea/), but you are free to use alternatives such as [VS Code](https://code.visualstudio.com/) with suitable extensions.
* Import the project into your IDE. In IntelliJ, you can do this via `File` -> `Open` and selecting the root-level `build.gradle.kts`. If you have the `mise` [plugin](https://plugins.jetbrains.com/plugin/24904-mise) installed, IntelliJ will ask you to select the appropriate tool versions.
* Ensure that your IDE as initialized the project correctly, including all `src`, `test`, and `resources` folders.

## Build application

First, make sure that the Docker daemon is running (the tests use Testcontainers).
Then, to build the application, run the following command in the command line (or use the Gradle integration of your IDE):

```shell
gradle build
```
**Note:** The application does not load any data on startup. In the `dev` profile you can seed or reset it on demand — see [Managing data](#managing-data-dev-profile-only).

You can use the quiet mode to suppress most log messages:

```shell
gradle build -q
```

The Kotlin code is formatted and linted with [ktlint](https://pinterest.github.io/ktlint/). `gradle build`
fails on formatting violations (the `ktlintCheck` task runs as part of `check`); apply the fixes with:

```shell
gradle ktlintFormat
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

## Start application

First, make sure that the Docker daemon is running.
Before you start the application, you first need to start a Postgres docker container:

```shell
docker run -d --name db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:17-alpine
```

Then, you can start the application in the `dev` profile for local development:

```shell
gradle :application:bootRun --args='--spring.profiles.active=dev'
```
**Note:** The data source is configured via the [`application.yaml`](application/src/main/resources/application.yaml) file.

## Explore the REST API

### OpenAPI specification

After starting the application in the `dev` profile, you can access the OpenAPI specification (JSON) at [`http://localhost:8080/api/api-docs`](http://localhost:8080/api/api-docs).<br/>
You can also access the Swagger UI to interactively explore the API at [`http://localhost:8080/api/swagger-ui.html`](http://localhost:8080/api/swagger-ui.html).

### Local testing

You can use `curl` in the command line to send HTTP requests to the REST API.

#### POS endpoints (/api/pos)

**Get POS:**

All POS:
```shell
curl http://localhost:8080/api/pos
```

POS by ID:
```shell
curl http://localhost:8080/api/pos/1 # add valid POS id here
```

POS by name:
```shell
curl http://localhost:8080/api/pos/filter?name=Schmelzpunkt # add valid POS name here
```

##### Create POS

Create a POS based on a JSON object provided in the request body:

```shell
curl --request POST --header "Content-Type: application/json" --data '{"name":"New Café","description":"Description","type":"CAFE","campus":"ALTSTADT","street":"Hauptstraße","houseNumber":"100","postalCode":69117,"city":"Heidelberg"}' http://localhost:8080/api/pos
```

Create a POS based on an OpenStreetMap node:

```shell
curl --request POST http://localhost:8080/api/pos/import/osm/5589879349?campus_type=ALTSTADT # set a valid OSM node ID here
```

IDs for testing:
* 5589879349 (Rada Coffee & Rösterei in ALTSTADT)
* 1864600258 (La Fée in ALTSTADT)
* 1864600236 (Café Moro in ALTSTADT) --> missing address

See bean validation in action:

```shell
curl --header "Content-Type: application/json" --request POST -i --data '{"name":"","description":"","type":"CAFE","campus":"ALTSTADT","street":"Hauptstraße","houseNumber":"100","postalCode":69117,"city":"Heidelberg"}' http://localhost:8080/api/pos
```

##### Update POS

Update title and description:
```shell
curl --header "Content-Type: application/json" --request PUT --data '{"id":4,"name":"New coffee","description":"Great croissants","type":"CAFE","campus":"ALTSTADT","street":"Hauptstraße","houseNumber":"95","postalCode":69117,"city":"Heidelberg"}' http://localhost:8080/api/pos/4 # set correct POS id here and in the body
```

##### Delete POS

Delete POS by ID:
```shell
curl --request DELETE -i http://localhost:8080/api/pos/1 # set existing POS ID here
```

#### Users endpoints (/api/users)

##### Get users

All users:
```shell
curl http://localhost:8080/api/users
```

User by ID:
```shell
curl http://localhost:8080/api/users/1 # add valid user id here
```

User by login name:
```shell
curl http://localhost:8080/api/users/filter?login_name=jane_doe # add valid user login name here
```

##### Create users

```shell
curl --header "Content-Type: application/json" --request POST --data '{"loginName":"other_login_name","emailAddress":"other.person@uni-heidelberg.de","firstName":"New","lastName":"Person"}' http://localhost:8080/api/users
```

See bean validation in action:
```shell
curl --header "Content-Type: application/json" --request POST -i --data '{"loginName":"other_login_name!","emailAddress":"other.personATuni-heidelberg.de","firstName":"","lastName":""}' http://localhost:8080/api/users
```

##### Update user

Update the login name and the email address:
```shell
curl --header "Content-Type: application/json" --request PUT --data '{"id":1,"createdAt":"2025-06-03T12:00:00","updatedAt":"2025-06-03T12:00:00","loginName":"jane_doe_new","emailAddress":"jane.doe.new@uni-heidelberg.de","firstName":"Jane","lastName":"Doe"}' http://localhost:8080/api/users/1 # set correct user id here and in the body
```

##### Delete user

Delete user by ID:
```shell
curl --request DELETE -i http://localhost:8080/api/users/1 # set existing POS ID here
```

#### Reviews endpoint (/api/reviews)

##### Get reviews

All reviews:
```shell
curl http://localhost:8080/api/reviews
```

Review by ID:
```shell
curl http://localhost:8080/api/reviews/1 # add valid user id here
```

Get approved reviews for a POS:
```shell
curl 'http://localhost:8080/api/reviews/filter?pos_id=1&approved=true' # add valid POS id here; quote the URL so the shell does not treat & as a job control operator
```

##### Create reviews

```shell
curl --header "Content-Type: application/json" --request POST --data '{"posId":2,"authorId":1,"review":"Great place!"}' http://localhost:8080/api/reviews # use existing IDs for posId and authorId
```

Users cannot create more than one review per POS:
```shell
curl --header "Content-Type: application/json" --request POST --data '{"posId":2,"authorId":1,"review":"Great place!"}' http://localhost:8080/api/reviews # use existing IDs for posId and authorId
```

##### Approve reviews

Users cannot approve their own reviews:
```shell
curl --request PUT http://localhost:8080/api/reviews/4/approve?user_id=1 # use existing review ID and user ID (of the author)
```

However, users can approve the same review multiple times (which is a limitation of the current implementation):
```shell
curl --request PUT http://localhost:8080/api/reviews/4/approve?user_id=2 # use existing review ID and user ID (different from author)
```
```shell
curl --request PUT http://localhost:8080/api/reviews/4/approve?user_id=2 # use existing review ID and user ID (different from author)
```
```shell
curl --request PUT http://localhost:8080/api/reviews/4/approve?user_id=2 # use existing review ID and user ID (different from author)
```

## Managing data (`dev` profile only)

The application does not load any data on startup (in any profile), and the database persists across
application restarts. In the `dev` profile, three endpoints let you inspect, replace, and clear the
data on demand (they are not registered outside `dev`):

```shell
# report the current counts ({users, pos, reviews})
curl http://localhost:8080/api/dev/data

# replace the data with the fixture dataset (idempotent: clears first, safe to repeat)
curl --request PUT http://localhost:8080/api/dev/data

# clear all data
curl --request DELETE http://localhost:8080/api/dev/data
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
docker run -d --name db --net campus-coffee-net -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:17-alpine
docker run --net campus-coffee-net -e SPRING_PROFILES_ACTIVE=dev -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/postgres -p 8080:8080  -it --rm campus-coffee:latest
```

Explanation of selected options:

`docker run -p 8080:8080 ` runs the container with port 8080 exposed to the host machine. You can change the port mapping if needed.
`docker run ... -it`  runs a container in interactive mode with a pseudo-TTY (terminal).
`docker run ... --rm` automatically removes the container (and its associated resources) if it exists already.

Both run methods start the app in the `dev` profile. Since the application does not load data on startup, the API comes up empty — seed it with `PUT /api/dev/data` (see [Managing data](#managing-data-dev-profile-only)).

#### Use Docker compose to run the app container together with the DB container

Build container image:

```shell
docker compose build
```

Delete existing DB container (if you manually created it before):

```shell
docker rm -f db 2>/dev/null || true
```

Create and start containers:

```shell
docker compose down && docker compose up
```

Stop and remove containers and networks:

```shell
docker compose down
```

The `db` service has no named volume, so `docker compose down` discards its data and the next `docker compose up` starts with an empty database — reseed it with `POST /api/dev/data`.

## Deployment

### Deploy CampusCoffee to Google Cloud

We use the `gcloud` CLI (see [`mise.toml`](mise.toml)) to build CampusCoffee in the cloud using Cloud Build
and deploy it to Cloud Run.
Deployment using `compose.yaml` is [still in preview](https://docs.cloud.google.com/run/docs/deploy-run-compose); install the `beta` component first (`gcloud` prompts to install any other required components on first run).

Install the required `gcloud` CLI component:

```shell
gcloud components install beta
```

Log in and deploy the application using the `compose.yaml` file:

```shell
gcloud auth login
gcloud beta run compose up compose.yaml
```

Example output:
````
$ gcloud beta run compose up compose.yaml
Deploying from Compose to Cloud Run...
Please specify a region:
[1] africa-south1
[2] asia-east1
[3] asia-east2
...
[18] europe-west1
...
[44] cancel
Please enter numeric choice or text value (must exactly match list item):  18

To make this the default region, run `gcloud config set run/region europe-west1`.

Region set to europe-west1. You can change the region with gcloud config set run/region europe-west1.

✓ Setting up resources...
✓ Building container app from source... Logs are available at [https://console.cloud.google.com/cloud-build/builds;region=europe-west1
/c6871612-0596-4868-bd58-c9eaa1cb6bc4?project=797467293729].
Resource setup complete.
Deploying service 'campus-coffee' in project 'sotorrent-org' in region 'europe-west1'.
✓ Updating service 'campus-coffee'... Done.
✓ Creating Revision...
✓ Routing traffic...
Service 'campus-coffee' has been deployed.
Service URL: https://campus-coffee-4dx5ftg7eq-ew.a.run.app
