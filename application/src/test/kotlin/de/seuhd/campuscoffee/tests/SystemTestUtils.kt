package de.seuhd.campuscoffee.tests

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.api.dtos.UserDto
import de.seuhd.campuscoffee.domain.model.objects.Role
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.lang.reflect.Array as ReflectArray

/**
 * Utilities for the system tests.
 */
object SystemTestUtils {
    /**
     * Client bound to the running server for the current test. It is `lateinit` because the value depends
     * on the embedded server's port, which exists only after the server starts, so it is set in
     * [configureClient] rather than at construction.
     */
    private lateinit var client: RestTestClient

    /**
     * The credentials a request authenticates with (HTTP Basic). The seeded fixture users below carry the
     * roles needed for the write paths; tests that exercise ownership or role rules act as a specific user.
     */
    data class Credentials(
        val loginName: String,
        val password: String
    )

    /** Seeded fixture credentials, derived from [TestFixtures] so each password is defined in one place. */
    val ADMIN = credentialsFor(Role.ADMIN)
    val MODERATOR = credentialsFor(Role.MODERATOR)
    val USER = credentialsFor(Role.USER)

    /** An admin who is not also a moderator, to show content moderation is gated on MODERATOR, not ADMIN. */
    val ADMIN_NO_MOD =
        TestFixtures.adminWithoutModeration().let {
            Credentials(
                requireNotNull(it.loginName),
                requireNotNull(it.password)
            )
        }

    private fun credentialsFor(role: Role): Credentials =
        TestFixtures.rawCredentialsFor(role).let { (login, password) -> Credentials(login, password) }

    /**
     * The credentials the CRUD helpers and [client] authenticate with by default. Defaults to the admin
     * fixture (full roles), so a write request succeeds unless a test narrows the actor. Reset before each test by
     * the base class so a per-test override does not leak.
     */
    var defaultCredentials: Credentials = ADMIN

    /** Binds the shared [RestTestClient] to the running server on the given port. */
    fun configureClient(port: Int) {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        defaultCredentials = ADMIN
    }

    /** The Basic-auth header value for the given credentials, as Spring's [HttpHeaders.setBasicAuth] builds it. */
    fun basicAuthHeader(credentials: Credentials): String =
        HttpHeaders()
            .apply { setBasicAuth(credentials.loginName, credentials.password) }
            .getFirst(HttpHeaders.AUTHORIZATION)!!

    /** The client bound to the running server, for tests that call endpoints outside the CRUD helpers. */
    fun client(): RestTestClient = client

    // Creates a PostgreSQL testcontainer. The container is AutoCloseable but deliberately not closed here:
    // callers keep it open for the whole test run and Testcontainers tears it down on JVM shutdown, so
    // suppress the resource leak inspection.
    @Suppress("resource")
    fun getPostgresContainer(): PostgreSQLContainer<*> =
        // PostgreSQLContainer is a Java class whose type parameter refers back to itself
        // (PostgreSQLContainer<SELF extends PostgreSQLContainer<SELF>>). Testcontainers uses this so the
        // fluent withX() setters return the concrete subclass type, letting them chain across the class
        // hierarchy. Kotlin cannot express that self-reference, so we pass Nothing as the type argument; this
        // is the standard way to use Testcontainers from Kotlin. We don't set a custom username/password here
        // and rely on Testcontainers' defaults, which configurePostgresContainers() (below) hands to Spring as
        // the datasource URL, username, and password.
        PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:17-alpine"))

    /** Points the Spring datasource at the given PostgreSQL testcontainer. */
    fun configurePostgresContainers(
        registry: DynamicPropertyRegistry,
        postgresContainer: PostgreSQLContainer<*>
    ) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
        registry.add("spring.datasource.username", postgresContainer::getUsername)
        registry.add("spring.datasource.password", postgresContainer::getPassword)
    }

    /** Asserts two objects are equal, ignoring the given fields. */
    fun <T> assertEqualsIgnoringFields(
        actual: T,
        expected: T,
        vararg fieldsToIgnore: String
    ) {
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields(*fieldsToIgnore)
            // a user's role set has no order; the read path yields an EnumSet, the fixtures a LinkedHashSet
            .ignoringCollectionOrder()
            .isEqualTo(expected)
    }

    // A user's secrets never survive a response round-trip. The raw password is write-only and the stored
    // hash is never serialized; both are ignored so a created or fetched user compares equal to its fixture.
    private val secretFields = arrayOf("password", "passwordHash")

    /** Asserts two objects are equal, ignoring the timestamp (and user secret) fields. */
    fun <T> assertEqualsIgnoringTimestamps(
        actual: T,
        expected: T
    ) = assertEqualsIgnoringFields(actual, expected, "createdAt", "updatedAt", *secretFields)

    /** Asserts two objects are equal, ignoring the id, timestamp (and user secret) fields. */
    fun <T> assertEqualsIgnoringIdAndTimestamps(
        actual: T,
        expected: T
    ) = assertEqualsIgnoringFields(actual, expected, "id", "createdAt", "updatedAt", *secretFields)

    /** Asserts two lists contain the same elements (any order), ignoring the given fields per element. */
    fun <T> assertEqualsIgnoringFields(
        actual: List<T>,
        expected: List<T>,
        vararg fieldsToIgnore: String
    ) {
        val config =
            RecursiveComparisonConfiguration
                .builder()
                .withIgnoredFields(*fieldsToIgnore)
                // a user's role set has no order; the read path yields an EnumSet, the fixtures a LinkedHashSet
                .withIgnoreCollectionOrder(true)
                .build()
        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparator(config)
            .containsExactlyInAnyOrderElementsOf(expected)
    }

    /** Asserts two lists contain the same elements (any order), ignoring the timestamp (and user secret) fields. */
    fun <T> assertEqualsIgnoringTimestamps(
        actual: List<T>,
        expected: List<T>
    ) = assertEqualsIgnoringFields(actual, expected, "createdAt", "updatedAt", *secretFields)

    /**
     * Reusable CRUD operations over [RestTestClient] against the server bound by [configureClient].
     *
     * @param basePath the base path of the API endpoint
     * @param dtoClass the DTO class of the entities being tested
     * @param idGetter extracts the id from a DTO
     * @param requestBody the value sent as the request body for a DTO; defaults to the DTO itself. The
     *   user requests override it so the write-only password reaches the server, which the client would
     *   otherwise drop when it serializes the DTO (see [userRequests]).
     */
    class Requests<T : Any>(
        private val basePath: String,
        private val dtoClass: Class<T>,
        private val idGetter: (T) -> UUID?,
        private val requestBody: (T) -> Any = { it }
    ) {
        /** The DTO body of a response, after asserting the expected status. */
        private fun body(
            response: RestTestClient.ResponseSpec,
            expected: HttpStatus
        ): T {
            val result = response.returnResult(dtoClass)
            assertThat(result.status.value()).isEqualTo(expected.value())
            return result.responseBody!!
        }

        /** The raw status code of a response, without asserting it. */
        private fun status(response: RestTestClient.ResponseSpec): Int =
            response.returnResult<ByteArray>().status.value()

        /** The list body of a response, deserialized via the DTO array type, after asserting 200. */
        @Suppress("UNCHECKED_CAST")
        private fun list(response: RestTestClient.ResponseSpec): List<T> {
            val arrayType = ReflectArray.newInstance(dtoClass, 0).javaClass as Class<Array<T>>
            val result = response.returnResult(arrayType)
            assertThat(result.status.value()).isEqualTo(HttpStatus.OK.value())
            return result.responseBody?.toList() ?: emptyList()
        }

        // Reads are public for POS/reviews (credentials = null); user reads need authentication, so the
        // user tests pass credentials, which adds a Basic-auth header to the request.
        private fun RestTestClient.RequestHeadersSpec<*>.withOptionalAuth(credentials: Credentials?) =
            apply { credentials?.let { header(HttpHeaders.AUTHORIZATION, basicAuthHeader(it)) } }

        fun retrieveAll(credentials: Credentials? = null): List<T> =
            list(
                client
                    .get()
                    .uri(basePath)
                    .accept(MediaType.APPLICATION_JSON)
                    .withOptionalAuth(credentials)
                    .exchange()
            )

        /** Lists all and returns the raw status code (to assert a 401/403 when the read is not public). */
        fun retrieveAllStatusCode(credentials: Credentials? = null): Int =
            status(
                client
                    .get()
                    .uri(basePath)
                    .accept(MediaType.APPLICATION_JSON)
                    .withOptionalAuth(credentials)
                    .exchange()
            )

        fun retrieveById(
            id: UUID,
            credentials: Credentials? = null
        ): T =
            body(
                client
                    .get()
                    .uri("$basePath/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .withOptionalAuth(credentials)
                    .exchange(),
                HttpStatus.OK
            )

        fun retrieveByFilter(
            filterParameter: String,
            filterValue: String,
            credentials: Credentials? = null
        ): T =
            body(
                client
                    .get()
                    .uri("$basePath/filter?$filterParameter={value}", filterValue)
                    .accept(MediaType.APPLICATION_JSON)
                    .withOptionalAuth(credentials)
                    .exchange(),
                HttpStatus.OK
            )

        /** Filters by a parameter and returns the raw status code (to assert a 404 on a filter miss, or 401/403). */
        fun retrieveByFilterStatusCode(
            filterParameter: String,
            filterValue: String,
            credentials: Credentials? = null
        ): Int =
            status(
                client
                    .get()
                    .uri("$basePath/filter?$filterParameter={value}", filterValue)
                    .accept(MediaType.APPLICATION_JSON)
                    .withOptionalAuth(credentials)
                    .exchange()
            )

        fun create(
            entityList: List<T>,
            credentials: Credentials = defaultCredentials
        ): List<T> =
            entityList.map { dto ->
                body(
                    client
                        .post()
                        .uri(basePath)
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody(dto))
                        .exchange(),
                    HttpStatus.CREATED
                )
            }

        fun createAndReturnStatusCodes(
            entityList: List<T>,
            credentials: Credentials = defaultCredentials
        ): List<Int> =
            entityList.map { dto ->
                status(
                    client
                        .post()
                        .uri(basePath)
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody(dto))
                        .exchange()
                )
            }

        /** Creates without an Authorization header (to assert a 401 on an unauthenticated write request). */
        fun createUnauthenticatedAndReturnStatusCode(dto: T): Int =
            status(
                client
                    .post()
                    .uri(basePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(dto))
                    .exchange()
            )

        fun update(
            entityList: List<T>,
            credentials: Credentials = defaultCredentials
        ): List<T> =
            entityList.map { dto ->
                body(
                    client
                        .put()
                        .uri("$basePath/{id}", idGetter(dto))
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody(dto))
                        .exchange(),
                    HttpStatus.OK
                )
            }

        fun deleteAndReturnStatusCodes(
            idList: List<UUID>,
            credentials: Credentials = defaultCredentials
        ): List<Int> =
            idList.map { id ->
                status(
                    client
                        .delete()
                        .uri("$basePath/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                        .exchange()
                )
            }

        /** Filters by several query parameters, returning a list (the reviews filter returns many). */
        fun retrieveByFilter(params: Map<String, Any>): List<T> =
            list(
                client
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder.path("$basePath/filter")
                        params.forEach { (key, value) -> uriBuilder.queryParam(key, value) }
                        uriBuilder.build()
                    }.accept(MediaType.APPLICATION_JSON)
                    .exchange()
            )

        /** Retrieves by id and returns the raw status code (to assert a 404, or a 401/403 on a guarded read). */
        fun retrieveByIdStatusCode(
            id: UUID,
            credentials: Credentials? = null
        ): Int =
            status(
                client
                    .get()
                    .uri("$basePath/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .withOptionalAuth(credentials)
                    .exchange()
            )

        /** Updates with an explicit path id that may differ from the body id (to assert a 400 on mismatch). */
        fun updateWithPathIdAndReturnStatusCode(
            pathId: UUID,
            dto: T,
            credentials: Credentials = defaultCredentials
        ): Int =
            status(
                client
                    .put()
                    .uri("$basePath/{id}", pathId)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(dto))
                    .exchange()
            )

        /** Updates and returns the status codes (to assert a 404 when updating a missing entity, or a 403). */
        fun updateAndReturnStatusCodes(
            entityList: List<T>,
            credentials: Credentials = defaultCredentials
        ): List<Int> =
            entityList.map { dto ->
                status(
                    client
                        .put()
                        .uri("$basePath/{id}", idGetter(dto))
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody(dto))
                        .exchange()
                )
            }

        /** Approves an entity as the authenticated user via PUT /{id}/approve (the approver is the caller). */
        fun approve(
            id: UUID,
            credentials: Credentials = defaultCredentials
        ): T =
            body(
                client
                    .put()
                    .uri("$basePath/{id}/approve", id)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                    .exchange(),
                HttpStatus.OK
            )

        /** Approves and returns the raw status code (to assert a 400 self-approval, 404, or 409 repeat). */
        fun approveAndReturnStatusCode(
            id: UUID,
            credentials: Credentials = defaultCredentials
        ): Int =
            status(
                client
                    .put()
                    .uri("$basePath/{id}/approve", id)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(credentials))
                    .exchange()
            )

        /** Approves with a raw bearer token (to assert a JWT-authenticated write request or an expired/forged 401). */
        fun approveWithBearerAndReturnStatusCode(
            id: UUID,
            token: String
        ): Int =
            status(
                client
                    .put()
                    .uri("$basePath/{id}/approve", id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .exchange()
            )

        /** Creates with a raw bearer token (to assert that a JWT-authenticated write request obeys the same rules). */
        fun createWithBearerAndReturnStatusCode(
            dto: T,
            token: String
        ): Int =
            status(
                client
                    .post()
                    .uri(basePath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(dto))
                    .exchange()
            )
    }

    val posRequests = Requests("/api/pos", PosDto::class.java, idGetter = { it.id })

    // A UserDto's password is write-only, so the client drops it when it serializes the DTO. The user
    // requests send a map that carries the password instead, which is the body a real client would post.
    val userRequests =
        Requests("/api/users", UserDto::class.java, idGetter = { it.id }, requestBody = ::userRequestBody)
    val reviewRequests = Requests("/api/reviews", ReviewDto::class.java, idGetter = { it.id })

    /**
     * The request body for writing a [UserDto]. The write-only [UserDto.password] survives serialization
     * only when it is sent as a plain map entry; a field a request omits is left out so the server's
     * "omitted means unchanged" rule (on update) and its validation (on create) see the same body a real
     * client would send.
     */
    private fun userRequestBody(dto: UserDto): Map<String, Any?> =
        buildMap {
            dto.id?.let { put("id", it) }
            put("loginName", dto.loginName)
            put("emailAddress", dto.emailAddress)
            put("firstName", dto.firstName)
            put("lastName", dto.lastName)
            dto.password?.let { put("password", it) }
            dto.roles?.let { put("roles", it) }
        }
}
