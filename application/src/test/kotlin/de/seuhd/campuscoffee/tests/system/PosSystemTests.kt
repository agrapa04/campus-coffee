package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.ADMIN_NO_MOD
import de.seuhd.campuscoffee.tests.SystemTestUtils.MODERATOR
import de.seuhd.campuscoffee.tests.SystemTestUtils.USER
import de.seuhd.campuscoffee.tests.SystemTestUtils.assertEqualsIgnoringIdAndTimestamps
import de.seuhd.campuscoffee.tests.SystemTestUtils.assertEqualsIgnoringTimestamps
import de.seuhd.campuscoffee.tests.SystemTestUtils.basicAuthHeader
import de.seuhd.campuscoffee.tests.SystemTestUtils.client
import de.seuhd.campuscoffee.tests.SystemTestUtils.posRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.returnResult
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * System tests for the operations related to POS (Point of Sale). Curating a POS requires a moderator,
 * so the create/update/delete helpers authenticate as the admin fixture (which holds MODERATOR) by
 * default; the role-gate test below pins the 401/403/2xx outcomes per the access-control matrix.
 */
open class PosSystemTests : AbstractSystemTest() {
    @Test
    fun `creating a POS returns it with the same field values`() {
        val posToCreate = TestFixtures.getPosFixturesForInsertion().first()
        val createdPos =
            posDtoMapper.toDomain(
                posRequests.create(listOf(posDtoMapper.fromDomain(posToCreate))).first()
            )

        assertEqualsIgnoringIdAndTimestamps(createdPos, posToCreate)
    }

    @Test
    fun `creating a POS returns a Location header pointing at the new resource`() {
        val dto = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first())

        val result =
            client()
                .post()
                .uri("/api/pos")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(MODERATOR))
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto)
                .exchange()
                .returnResult<PosDto>()

        assertThat(result.status.value()).isEqualTo(HttpStatus.CREATED.value())
        assertThat(result.responseHeaders.location.toString()).endsWith("/api/pos/${result.responseBody!!.id}")
    }

    @Test
    fun `curating a POS returns 403 for a USER or non-moderator admin and succeeds for a MODERATOR`() {
        val dto = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first())

        // an unauthenticated write request is rejected before it reaches the controller
        assertThat(posRequests.createUnauthenticatedAndReturnStatusCode(dto))
            .isEqualTo(HttpStatus.UNAUTHORIZED.value())

        // a plain USER lacks MODERATOR, so the create is forbidden
        assertThat(posRequests.createAndReturnStatusCodes(listOf(dto), USER).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())

        // an admin who is not also a moderator cannot moderate content either (roles are orthogonal)
        assertThat(posRequests.createAndReturnStatusCodes(listOf(dto), ADMIN_NO_MOD).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())

        // a moderator may create the POS
        val created = posRequests.create(listOf(dto), MODERATOR).first()

        // a USER also cannot update or delete it, while a moderator can
        assertThat(
            posRequests.updateAndReturnStatusCodes(listOf(created.copy(description = "Edited by a USER")), USER).first()
        ).isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(posRequests.deleteAndReturnStatusCodes(listOf(created.id!!), USER).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())

        val updated = posRequests.update(listOf(created.copy(description = "Edited by a moderator")), MODERATOR).first()
        assertThat(updated.description).isEqualTo("Edited by a moderator")
        assertThat(posRequests.deleteAndReturnStatusCodes(listOf(created.id!!), MODERATOR).first())
            .isEqualTo(HttpStatus.NO_CONTENT.value())
    }

    @Test
    fun `creating a POS with a street longer than 255 characters returns 400 Bad Request`() {
        // the street column is varchar(255); without the DTO @Size this overflowed to a database error (500)
        val dto =
            posDtoMapper
                .fromDomain(TestFixtures.getPosFixturesForInsertion().first())
                .copy(street = "a".repeat(256))

        assertThat(posRequests.createAndReturnStatusCodes(listOf(dto), MODERATOR).first())
            .isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `a PUT advances updatedAt even for a no-op update, identically in both persistence modes`() {
        val created =
            posRequests
                .create(
                    listOf(posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first()))
                ).first()
        // the timestamp column resolves to microseconds, so pause to guarantee a strictly later updatedAt
        Thread.sleep(10)

        // a no-op PUT (identical field values) must still advance updatedAt: the relational path forces it and
        // the event-sourcing projector always writes a fresh updatedAt, so the two modes agree
        val updated = posRequests.update(listOf(created)).first()

        assertThat(updated.updatedAt!!).isAfter(created.updatedAt!!)
        // createdAt is preserved on update. Compare at millisecond precision: the create response carries the
        // in-memory value (nanoseconds on some platforms) while a later read is truncated to the database's
        // microsecond precision, so an exact equality is platform-dependent.
        assertThat(updated.createdAt!!.truncatedTo(ChronoUnit.MILLIS))
            .isEqualTo(created.createdAt!!.truncatedTo(ChronoUnit.MILLIS))
    }

    @Test
    fun `listing all POS returns every created entry`() {
        val createdPosList = TestFixtures.createPosFixtures(posService)

        val retrievedPos = posRequests.retrieveAll().map(posDtoMapper::toDomain)

        assertEqualsIgnoringTimestamps(retrievedPos, createdPosList)
    }

    @Test
    fun `fetching a POS by id returns it`() {
        val createdPos = TestFixtures.createPosFixtures(posService).first()

        val retrievedPos = posDtoMapper.toDomain(posRequests.retrieveById(createdPos.id!!))

        assertEqualsIgnoringTimestamps(retrievedPos, createdPos)
    }

    @Test
    fun `filtering POS by name returns the matching POS`() {
        val createdPos = TestFixtures.createPosFixtures(posService).first()
        val filteredPos = posDtoMapper.toDomain(posRequests.retrieveByFilter("name", createdPos.name))

        assertEqualsIgnoringTimestamps(filteredPos, createdPos)
    }

    @Test
    fun `updating a POS changes its fields and persists them`() {
        val original = TestFixtures.createPosFixtures(posService).first()

        // domain models are immutable, so derive the updated instance with copy()
        val posToUpdate = original.copy(name = original.name + " (Updated)", description = "Updated description")

        val updatedPos =
            posDtoMapper.toDomain(
                posRequests.update(listOf(posDtoMapper.fromDomain(posToUpdate))).first()
            )
        assertEqualsIgnoringTimestamps(updatedPos, posToUpdate)

        // verify changes persist
        val retrievedPos = posDtoMapper.toDomain(posRequests.retrieveById(posToUpdate.id!!))
        assertEqualsIgnoringTimestamps(retrievedPos, posToUpdate)
    }

    @Test
    fun `deleting a POS twice returns 204 No Content then 404 Not Found`() {
        val posToDelete = TestFixtures.createPosFixtures(posService).first()
        val id = requireNotNull(posToDelete.id)

        val statusCodes = posRequests.deleteAndReturnStatusCodes(listOf(id, id))

        // the first deletion returns 204 No Content, the second 404 Not Found
        assertThat(statusCodes).containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NOT_FOUND.value())

        val remainingPosIds: List<UUID?> = posRequests.retrieveAll().map { it.id }
        assertThat(remainingPosIds).doesNotContain(id)
    }

    @Test
    fun `the API serves JSON even when the client prefers XML`() {
        TestFixtures.createPosFixtures(posService)

        // a browser prefers XML (application/xml;q=0.9) but also accepts */*; the API must answer JSON
        val result =
            client()
                .get()
                .uri("/api/pos")
                .accept(MediaType.APPLICATION_XML, MediaType.ALL)
                .exchange()
                .returnResult<String>()

        assertThat(result.status.value()).isEqualTo(HttpStatus.OK.value())
        assertThat(result.responseBody).startsWith("[") // a JSON array, not <List>/<PosDto> XML
    }
}
