# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CampusCoffee is a Spring Boot application for managing Points of Sale (POS) like cafes and coffee shops on campus. It follows a **hexagonal (ports-and-adapters) architecture** with strict layer separation enforced by ArchUnit tests.

## Architecture

The project uses a **multi-module Maven structure** with four modules:

### Module Dependencies
- **domain**: Core business logic, domain models, and port interfaces (no external dependencies except validation).
- **api**: REST API layer with controllers, DTOs, and DTO mappers (depends on: domain).
- **data**: Data layer with JPA entities, repositories, and Feign clients (depends on: domain).
- **application**: Main Spring Boot application that wires everything together (depends on: domain, api, data at runtime).

### Layer Rules (Enforced by ArchUnit)
From `application/src/test/java/de/seuhd/campuscoffee/tests/architecture/ArchitectureTests.java:20-30`:

- **api** layer may only be accessed by **application**.
- **domain** layer may only be accessed by **api**, **data**, and **application**.
- **data** layer may only be accessed by **application**.
- **application** layer may not be accessed by any layer.

### Ports and Adapters Pattern

The domain defines **port interfaces** that adapters implement:

- **API Ports** (`domain/src/main/java/de/seuhd/campuscoffee/domain/ports/api/`): Generic service interface `CrudService<DOMAIN, ID>` and concrete service interfaces such as `PosService`, `UserService`, and `ReviewService`.
- **Data Ports** (`domain/src/main/java/de/seuhd/campuscoffee/domain/ports/data/`): Generic data service interface `CrudDataService<DOMAIN, ID>` and concrete service interfaces such as `PosDataService`, `UserDataService`, `ReviewDataService`, and `OsmDataService`.

Service **implementations**:
- API services in `domain/src/main/java/de/seuhd/campuscoffee/domain/implementation/`.
- Data services in `data/src/main/java/de/seuhd/campuscoffee/data/implementations/`.

### Generic Base Classes

The codebase uses extensive generics to reduce duplication:

- **CrudController** (`api/src/main/java/de/seuhd/campuscoffee/api/controller/CrudController.java`): Generic REST controller for CRUD operations.
- **CrudService** / **CrudServiceImpl**: Generic CRUD service interface and implementation.
- **CrudDataService** / **CrudDataServiceImpl**: Generic data service interface and implementation.
- **DtoMapper** / **EntityMapper**: Generic mapping interfaces using MapStruct.

Domain-specific controllers/services extend these base classes (e.g., `PosController extends CrudController<PosDto, Pos, Long>`).

## Build and Run Commands

### Prerequisites
- Docker daemon must be running to use a database in the `dev` profile or to run the tests that use *Testcontainers*.
- Java 21 and Maven 3.9 (configured via `mise.toml`)

### Build

```shell
mvn clean install
```

### Start PostgreSQL Database

```shell
docker run -d --name db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:17-alpine
```

### Run Application (dev profile)

```shell
cd application
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile:
- Enables Swagger UI at `http://localhost:8080/api/swagger-ui.html`.
- Enables API docs at `http://localhost:8080/api/api-docs`.
- Clears repositories and loads initial data on startup (see `application/src/main/java/de/seuhd/campuscoffee/LoadInitialData.java`).

### Run Tests

All tests:

```shell
mvn test
```

Single test class:

```shell
mvn test -Dtest=PosServiceTest
```

Single test method:

```shell
mvn test -Dtest=PosServiceTest#testMethodName
```

### Code Coverage and Mutation Testing

- **Coverage (JaCoCo)**: the `coverage` module aggregates execution data from all modules into one
  report at `coverage/target/site/jacoco-aggregate/index.html`. Aggregation is required because
  `domain`/`api`/`data` are largely covered by the `application` system and acceptance tests, not by
  their own tests (`data` has none). `mvn verify` builds the report and enforces the gate: the
  `check-aggregate` execution in `coverage/pom.xml` fails the build when aggregated line or branch
  coverage is below its configured minimums. The minimums track current coverage; raise them
  when adding tests, never lower them to make a build pass.
- **Mutation testing (PITest)**: opt-in and local via the `mutation` profile: `mvn -P mutation clean test`
  (use `clean` so stale reports are not reused). Each module runs PIT against its own tests and writes its
  own report under `<module>/target/pit-reports/index.html`: `domain` mutates `domain.*`, `api` mutates
  `api.*`, and `data` mutates `data.*`, each against that module's own tests; `application` mutates
  `api.*`/`data.*` via `crossModule` against the system and acceptance tests. The api/data classes
  therefore appear in two reports (a module's own run and application's system run), which are not merged:
  PIT's `report-aggregate` overwrites rather than unions duplicate mutations, so a merge would discard one
  side. Read a module's report for what its own tests catch and `application`'s for the system tests; the
  controllers, which have no api-local tests, are killed only in the application report. The generated
  `*MapperImpl` classes are excluded from mutation, mirroring the JaCoCo gate. Per-module targets live in
  each module's pom; shared config is in the root `mutation` profile's `pluginManagement`. Select the
  mutator group with `-Dpitest.mutators=DEFAULTS|STRONGER|ALL`.
- When adding a feature, also add tests; use surviving mutants to find missing assertions. The
  hand-written mapping logic in `PosEntityMapper` (house-number parsing), `ReviewDtoMapper` (expression
  mappings), and `HouseNumberConverter` contains real logic and is kept in scope for both tools.

### Docker

Build image:

```shell
docker build -t campus-coffee:latest .
```

Run with Docker Compose:

```shell
docker compose down && docker compose up
```

## Database

- **Database**: PostgreSQL 17.
- **Migrations**: Flyway (located in `data/src/main/resources/db/migration/`).
- **ORM**: JPA with Spring Data.
- **Connection**: Configured in `application/src/main/resources/application.yaml`.

Migration files follow Flyway naming convention (e.g., `V1__create_pos_table.sql`, `V2__create_users_table.sql`).

## Testing Strategy

- **Unit and Integration Tests**: In `domain/src/test/java/` (e.g., `PosServiceTest`, `ReviewServiceTest`)
- **System Tests**: In `application/src/test/java/de/seuhd/campuscoffee/tests/system/` (e.g., `PosSystemTests`, `UsersSystemTests`)
  - Use Testcontainers for PostgreSQL.
  - Use RestAssured for API testing.
  - Extend `AbstractSysTest` base class.
- **Acceptance Tests**: In `application/src/test/java/de/seuhd/campuscoffee/tests/acceptance/`
  - Cucumber BDD tests with `.feature` files in `application/src/test/resources/de/seuhd/campuscoffee/tests/acceptance/`
  - Step definitions in `CucumberPosSteps.java`
- **Architecture Tests**: In `application/src/test/java/de/seuhd/campuscoffee/tests/architecture/`
  - ArchUnit tests enforce hexagonal architecture rules

## Key Technologies

- **Spring Boot 3.5.8** with Spring Cloud 2025.0.0.
- **Java 21** with JSpecify annotations for nullability.
- **Lombok** for boilerplate reduction.
- **MapStruct** for object mapping (DTOs <-> domain models <-> entities).
- **Bean Validation** (Jakarta Validation) for input validation (validation happens in the controllers based on the DTOs, before mapping them to domain models).
- **OpenAPI/Swagger** (SpringDoc) for API documentation.
- **Feign** for REST client (OpenStreetMap API integration).
- **Testcontainers** for system tests.
- **Cucumber** for BDD acceptance tests.
- **ArchUnit** for architecture testing.

## Important Patterns

### Error Handling

Domain exceptions in `domain/src/main/java/de/seuhd/campuscoffee/domain/exceptions/`:
- `NotFoundException`: Entity not found.
- `DuplicationException`: Duplicate unique fields.
- `ValidationException`: Business rule violation.
- `MissingFieldException`: Required field missing.

Global exception handler: `api/src/main/java/de/seuhd/campuscoffee/api/exceptions/GlobalExceptionHandler.java`.

### MapStruct Configuration

The project uses Lombok with MapStruct. Annotation processor configuration is in the root `pom.xml` with `lombok-mapstruct-binding` to ensure correct ordering.

### Custom Sequence Generation

JPA entities use custom sequence generators defined in `data/src/main/java/de/seuhd/campuscoffee/data/persistence/generators/` to allow resetting sequences when running in the `dev` profile.

### OpenAPI Customization

Custom OpenAPI annotations in `api/src/main/java/de/seuhd/campuscoffee/api/openapi/`:
- `@CrudOperation` for common CRUD operations.
- `CrudOperationCustomizer` for customizing OpenAPI spec.
- Reduces repetitive annotations in controllers.

## Configuration

- Main config: `application/src/main/resources/application.yaml`.
- Dev profile activates on `spring.config.activate.on-profile: dev`.
- Custom properties:
  - `osm.api.base-url`: OpenStreetMap API endpoint.
  - `campus-coffee.approval.min-count`: Minimum number of approvals needed for reviews to be approved.

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
- `PUT /reviews/{id}/approve` - Approve review (requires different user than author).

## Working with the Codebase

### Adding a New Entity

1. Create domain model in `domain/src/main/java/de/seuhd/campuscoffee/domain/model/objects/`.
2. Create service interface in `domain/src/main/java/de/seuhd/campuscoffee/domain/ports/api/` (extend `CrudService<DOMAIN, ID>`).
3. Create data service interface in `domain/src/main/java/de/seuhd/campuscoffee/domain/ports/data/` (extend `CrudDataService<DOMAIN, ID>`).
4. Create service implementation in `domain/src/main/java/de/seuhd/campuscoffee/domain/implementation/` (extend `CrudServiceImpl<DOMAIN, ID>`).
5. Create JPA entity in `data/src/main/java/de/seuhd/campuscoffee/data/persistence/entities/`.
6. Create repository in `data/src/main/java/de/seuhd/campuscoffee/data/persistence/repositories/` (extend `JpaRepository`).
7. Create entity mapper in `data/src/main/java/de/seuhd/campuscoffee/data/mapper/` (extend `EntityMapper`).
8. Create data service implementation in `data/src/main/java/de/seuhd/campuscoffee/data/implementations/` (extend `CrudDataServiceImpl<DOMAIN, ENTITY, RESPOSITORY, ID>`).
9. Create DTO in `api/src/main/java/de/seuhd/campuscoffee/api/dtos/` (extend `Dto<ID>`).
10. Create DTO mapper in `api/src/main/java/de/seuhd/campuscoffee/api/mapper/` (extend `DtoMapper<DOMAIN, DTO>`).
11. Create controller in `api/src/main/java/de/seuhd/campuscoffee/api/controller/` (extend `CrudController<DTO, DOMAIN, ID>`).
12. Create Flyway migration in `data/src/main/resources/db/migration/`.

### Constraint Violations

Database uniqueness constraints are automatically converted to `DuplicationException` via `ConstraintMapping` in `data/src/main/java/de/seuhd/campuscoffee/data/constraints/`. Register custom constraint mappings there.
