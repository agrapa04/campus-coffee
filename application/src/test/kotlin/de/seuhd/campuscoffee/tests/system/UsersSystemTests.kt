package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.assertEqualsIgnoringIdAndTimestamps
import de.seuhd.campuscoffee.tests.SystemTestUtils.assertEqualsIgnoringTimestamps
import de.seuhd.campuscoffee.tests.SystemTestUtils.userRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * System tests for the operations related to Users.
 */
class UsersSystemTests : AbstractSystemTest() {
    @Test
    fun `creating a user returns it with the same field values`() {
        val userToCreate = TestFixtures.getUserFixturesForInsertion().first()
        val createdUser =
            userDtoMapper.toDomain(
                userRequests.create(listOf(userDtoMapper.fromDomain(userToCreate))).first()
            )

        assertEqualsIgnoringIdAndTimestamps(createdUser, userToCreate)
    }

    @Test
    fun `creating a user with an invalid login name returns 400 Bad Request`() {
        val invalidUser = TestFixtures.getUserFixturesForInsertion().first().copy(loginName = "-")
        val statusCode =
            userRequests
                .createAndReturnStatusCodes(listOf(userDtoMapper.fromDomain(invalidUser)))
                .first()

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `listing all users returns every created entry`() {
        val createdUserList = TestFixtures.createUserFixtures(userService)

        val retrievedUsers = userRequests.retrieveAll().map(userDtoMapper::toDomain)

        assertEqualsIgnoringTimestamps(retrievedUsers, createdUserList)
    }

    @Test
    fun `fetching a user by id returns it`() {
        val createdUser = TestFixtures.createUserFixtures(userService).first()

        val retrievedUser = userDtoMapper.toDomain(userRequests.retrieveById(createdUser.id!!))

        assertEqualsIgnoringTimestamps(retrievedUser, createdUser)
    }

    @Test
    fun `filtering users by login name returns the matching user`() {
        val createdUser = TestFixtures.createUserFixtures(userService).first()
        val filteredUser = userDtoMapper.toDomain(userRequests.retrieveByFilter("login_name", createdUser.loginName))

        assertEqualsIgnoringTimestamps(filteredUser, createdUser)
    }

    @Test
    fun `updating a user changes its fields and persists them`() {
        val original = TestFixtures.createUserFixtures(userService).first()

        val userToUpdate =
            original.copy(
                loginName = original.loginName + "_updated",
                emailAddress = "updated." + original.emailAddress
            )

        val updatedUser =
            userDtoMapper.toDomain(
                userRequests.update(listOf(userDtoMapper.fromDomain(userToUpdate))).first()
            )
        assertEqualsIgnoringTimestamps(updatedUser, userToUpdate)

        // verify changes persist
        val retrievedUser = userDtoMapper.toDomain(userRequests.retrieveById(userToUpdate.id!!))
        assertEqualsIgnoringTimestamps(retrievedUser, userToUpdate)
    }

    @Test
    fun `deleting a user twice returns 204 No Content then 404 Not Found`() {
        val userToDelete = TestFixtures.createUserFixtures(userService).first()
        val id = requireNotNull(userToDelete.id)

        val statusCodes = userRequests.deleteAndReturnStatusCodes(listOf(id, id))

        // the first deletion returns 204 No Content, the second 404 Not Found
        assertThat(statusCodes).containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NOT_FOUND.value())

        val remainingUserIds: List<Long?> = userRequests.retrieveAll().map { it.id }
        assertThat(remainingUserIds).doesNotContain(id)
    }
}
