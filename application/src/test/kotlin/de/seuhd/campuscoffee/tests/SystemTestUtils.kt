package de.seuhd.campuscoffee.tests

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.api.dtos.UserDto
import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.lang.reflect.Array as ReflectArray

/**
 * Utilities for the system tests.
 */
object SystemTestUtils {
    /** Client bound to the running server for the current test; set via [configureClient]. */
    private lateinit var client: RestTestClient

    /** Binds the shared [RestTestClient] to the running server on the given port. */
    fun configureClient(port: Int) {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    /** The client bound to the running server, for tests that call endpoints outside the CRUD helpers. */
    fun client(): RestTestClient = client

    /** Creates a PostgreSQL testcontainer. */
    @Suppress("resource")
    fun getPostgresContainer(): PostgreSQLContainer<*> =
        // PostgreSQLContainer's self-type is its own type parameter, so the fluent withX setters cannot be
        // chained on a <Nothing> instance; the default test credentials are read back via the dynamic properties.
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
        assertThat(actual).usingRecursiveComparison().ignoringFields(*fieldsToIgnore).isEqualTo(expected)
    }

    /** Asserts two objects are equal, ignoring the timestamp fields. */
    fun <T> assertEqualsIgnoringTimestamps(
        actual: T,
        expected: T
    ) = assertEqualsIgnoringFields(actual, expected, "createdAt", "updatedAt")

    /** Asserts two objects are equal, ignoring the id and timestamp fields. */
    fun <T> assertEqualsIgnoringIdAndTimestamps(
        actual: T,
        expected: T
    ) = assertEqualsIgnoringFields(actual, expected, "id", "createdAt", "updatedAt")

    /** Asserts two lists contain the same elements (any order), ignoring the given fields per element. */
    fun <T> assertEqualsIgnoringFields(
        actual: List<T>,
        expected: List<T>,
        vararg fieldsToIgnore: String
    ) {
        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields(*fieldsToIgnore)
            .containsExactlyInAnyOrderElementsOf(expected)
    }

    /** Asserts two lists contain the same elements (any order), ignoring the timestamp fields. */
    fun <T> assertEqualsIgnoringTimestamps(
        actual: List<T>,
        expected: List<T>
    ) = assertEqualsIgnoringFields(actual, expected, "createdAt", "updatedAt")

    /**
     * Reusable CRUD operations over [RestTestClient] against the server bound by [configureClient].
     *
     * @param basePath the base path of the API endpoint
     * @param dtoClass the DTO class of the entities being tested
     * @param idGetter extracts the id from a DTO
     */
    class Requests<T : Any>(
        private val basePath: String,
        private val dtoClass: Class<T>,
        private val idGetter: (T) -> Long?
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
            response.returnResult(ByteArray::class.java).status.value()

        /** The list body of a response, deserialized via the DTO array type, after asserting 200. */
        @Suppress("UNCHECKED_CAST")
        private fun list(response: RestTestClient.ResponseSpec): List<T> {
            val arrayType = ReflectArray.newInstance(dtoClass, 0).javaClass as Class<Array<T>>
            val result = response.returnResult(arrayType)
            assertThat(result.status.value()).isEqualTo(HttpStatus.OK.value())
            return result.responseBody?.toList() ?: emptyList()
        }

        fun retrieveAll(): List<T> =
            list(
                client
                    .get()
                    .uri(basePath)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
            )

        fun retrieveById(id: Long): T =
            body(
                client
                    .get()
                    .uri("$basePath/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange(),
                HttpStatus.OK
            )

        fun retrieveByFilter(
            filterParameter: String,
            filterValue: String
        ): T =
            body(
                client
                    .get()
                    .uri("$basePath/filter?$filterParameter={value}", filterValue)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange(),
                HttpStatus.OK
            )

        /** Filters by a parameter and returns the raw status code (to assert a 404 on a filter miss). */
        fun retrieveByFilterStatusCode(
            filterParameter: String,
            filterValue: String
        ): Int =
            status(
                client
                    .get()
                    .uri("$basePath/filter?$filterParameter={value}", filterValue)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
            )

        fun create(entityList: List<T>): List<T> =
            entityList.map { dto ->
                body(
                    client
                        .post()
                        .uri(basePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(dto)
                        .exchange(),
                    HttpStatus.CREATED
                )
            }

        fun createAndReturnStatusCodes(entityList: List<T>): List<Int> =
            entityList.map { dto ->
                status(
                    client
                        .post()
                        .uri(basePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(dto)
                        .exchange()
                )
            }

        fun update(entityList: List<T>): List<T> =
            entityList.map { dto ->
                body(
                    client
                        .put()
                        .uri("$basePath/{id}", idGetter(dto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(dto)
                        .exchange(),
                    HttpStatus.OK
                )
            }

        fun deleteAndReturnStatusCodes(idList: List<Long>): List<Int> =
            idList.map { id ->
                status(client.delete().uri("$basePath/{id}", id).exchange())
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

        /** Retrieves by id and returns the raw status code (to assert a 404). */
        fun retrieveByIdStatusCode(id: Long): Int =
            status(
                client
                    .get()
                    .uri("$basePath/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
            )

        /** Updates with an explicit path id that may differ from the body id (to assert a 400 on mismatch). */
        fun updateWithPathIdAndReturnStatusCode(
            pathId: Long,
            dto: T
        ): Int =
            status(
                client
                    .put()
                    .uri("$basePath/{id}", pathId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .exchange()
            )

        /** Updates and returns the status codes (to assert a 404 when updating a missing entity). */
        fun updateAndReturnStatusCodes(entityList: List<T>): List<Int> =
            entityList.map { dto ->
                status(
                    client
                        .put()
                        .uri("$basePath/{id}", idGetter(dto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(dto)
                        .exchange()
                )
            }

        /** Approves an entity on behalf of a user via PUT /{id}/approve?user_id=... (reviews). */
        fun approve(
            id: Long,
            userId: Long
        ): T =
            body(
                client.put().uri("$basePath/{id}/approve?user_id={userId}", id, userId).exchange(),
                HttpStatus.OK
            )

        /** Approves an entity and returns the raw status code (to assert a 400 self-approval or 404). */
        fun approveAndReturnStatusCode(
            id: Long,
            userId: Long
        ): Int = status(client.put().uri("$basePath/{id}/approve?user_id={userId}", id, userId).exchange())
    }

    val posRequests = Requests("/api/pos", PosDto::class.java) { it.id }
    val userRequests = Requests("/api/users", UserDto::class.java) { it.id }
    val reviewRequests = Requests("/api/reviews", ReviewDto::class.java) { it.id }
}
