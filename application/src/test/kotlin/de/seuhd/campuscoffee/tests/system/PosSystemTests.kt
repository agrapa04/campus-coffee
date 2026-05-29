package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.assertEqualsIgnoringIdAndTimestamps
import de.seuhd.campuscoffee.tests.SystemTestUtils.assertEqualsIgnoringTimestamps
import de.seuhd.campuscoffee.tests.SystemTestUtils.posRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * System tests for the operations related to POS (Point of Sale).
 */
class PosSystemTests : AbstractSysTest() {
    @Test
    fun createPos() {
        val posToCreate = TestFixtures.getPosFixturesForInsertion().first()
        val createdPos =
            posDtoMapper.toDomain(
                posRequests.create(listOf(posDtoMapper.fromDomain(posToCreate))).first()
            )

        assertEqualsIgnoringIdAndTimestamps(createdPos, posToCreate)
    }

    @Test
    fun getAllCreatedPos() {
        val createdPosList = TestFixtures.createPosFixtures(posService)

        val retrievedPos = posRequests.retrieveAll().map(posDtoMapper::toDomain)

        assertEqualsIgnoringTimestamps(retrievedPos, createdPosList)
    }

    @Test
    fun getPosById() {
        val createdPos = TestFixtures.createPosFixtures(posService).first()

        val retrievedPos = posDtoMapper.toDomain(posRequests.retrieveById(createdPos.id!!))

        assertEqualsIgnoringTimestamps(retrievedPos, createdPos)
    }

    @Test
    fun filterPosByName() {
        val createdPos = TestFixtures.createPosFixtures(posService).first()
        val filteredPos = posDtoMapper.toDomain(posRequests.retrieveByFilter("name", createdPos.name))

        assertEqualsIgnoringTimestamps(filteredPos, createdPos)
    }

    @Test
    fun updatePos() {
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
    fun deletePos() {
        val posToDelete = TestFixtures.createPosFixtures(posService).first()
        val id = requireNotNull(posToDelete.id)

        val statusCodes = posRequests.deleteAndReturnStatusCodes(listOf(id, id))

        // the first deletion returns 204 No Content, the second 404 Not Found
        assertThat(statusCodes).containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NOT_FOUND.value())

        val remainingPosIds: List<Long?> = posRequests.retrieveAll().map { it.id }
        assertThat(remainingPosIds).doesNotContain(id)
    }
}
