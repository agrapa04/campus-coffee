package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.api.dtos.UserDto
import de.seuhd.campuscoffee.domain.model.objects.Role
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.ADMIN
import de.seuhd.campuscoffee.tests.SystemTestUtils.Credentials
import de.seuhd.campuscoffee.tests.SystemTestUtils.USER
import de.seuhd.campuscoffee.tests.SystemTestUtils.assertEqualsIgnoringTimestamps
import de.seuhd.campuscoffee.tests.SystemTestUtils.userRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * System tests for the operations related to Users. The fixture users are seeded before each test (for
 * authentication), so the CRUD tests register *new* users with distinct login names rather than the
 * fixture ones, which would clash.
 */
class UsersSystemTests : AbstractSystemTest() {
    @Test
    fun `registering a user works without credentials and returns it without a password or hash`() {
        // registration is the one open write request; the create helper sends no Authorization header for it
        val created =
            userRequests.createUnauthenticatedAndReturnStatusCode(
                newUserDto("fresh_user", "fresh.user@uni-heidelberg.de")
            )
        assertThat(created).isEqualTo(HttpStatus.CREATED.value())

        val fetched = userRequests.retrieveByFilter("login_name", "fresh_user", ADMIN)
        assertThat(fetched.loginName).isEqualTo("fresh_user")
        assertThat(fetched.emailAddress).isEqualTo("fresh.user@uni-heidelberg.de")
        // a new account is a plain USER and the response never carries a password or hash
        assertThat(fetched.roles).containsExactly(Role.USER)
        assertThat(fetched.password).isNull()
    }

    @Test
    fun `registering with a short password returns 400 Bad Request`() {
        val invalid = newUserDto("short_pw_user", "short.pw@uni-heidelberg.de").copy(password = "short")

        assertThat(userRequests.createAndReturnStatusCodes(listOf(invalid)).first())
            .isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `registering with roles still yields a plain USER`() {
        // a client cannot self-assign a privileged role on registration
        val withRoles =
            newUserDto("would_be_admin", "would.be.admin@uni-heidelberg.de")
                .copy(roles = setOf(Role.ADMIN, Role.MODERATOR))

        val created = userRequests.create(listOf(withRoles)).first()

        assertThat(created.roles).containsExactly(Role.USER)
    }

    @Test
    fun `creating a user with an invalid login name returns 400 Bad Request`() {
        val invalid = newUserDto("bad", "bad@uni-heidelberg.de").copy(loginName = "-")

        assertThat(userRequests.createAndReturnStatusCodes(listOf(invalid)).first())
            .isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `listing all users as an admin returns the seeded users and every registered one`() {
        val created = userRequests.create(listOf(newUserDto("listed_user", "listed.user@uni-heidelberg.de"))).first()

        val logins = userRequests.retrieveAll(ADMIN).map { it.loginName }

        // every seeded fixture user plus the freshly registered one
        val seededLogins = TestFixtures.getUserFixtures().map { it.loginName }
        assertThat(logins).contains(*seededLogins.toTypedArray(), created.loginName)
        // no response carries a password or hash
        assertThat(userRequests.retrieveAll(ADMIN)).allSatisfy { assertThat(it.password).isNull() }
    }

    @Test
    fun `listing all users without credentials returns 401 Unauthorized`() {
        // user data is not public; an anonymous list is rejected before reaching the controller
        assertThat(userRequests.retrieveAllStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `listing all users as a plain USER returns 403 Forbidden`() {
        // listing every user (with their emails and roles) is admin-only
        assertThat(userRequests.retrieveAllStatusCode(USER)).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun `fetching a user by id as an admin returns it`() {
        val created = userRequests.create(listOf(newUserDto("fetched_user", "fetched.user@uni-heidelberg.de"))).first()

        val retrieved = userRequests.retrieveById(created.id!!, ADMIN)

        assertEqualsIgnoringTimestamps(userDtoMapper.toDomain(retrieved), userDtoMapper.toDomain(created))
    }

    @Test
    fun `a user may fetch their own profile while fetching another returns 403 Forbidden`() {
        val password = "view-own-only"
        val selfDto = newUserDto("viewer", "viewer@uni-heidelberg.de").copy(password = password)
        val self = userRequests.create(listOf(selfDto)).first()
        val selfCredentials = Credentials(self.loginName!!, password)

        // the user can read their own account
        val own = userRequests.retrieveById(self.id!!, selfCredentials)
        assertThat(own.loginName).isEqualTo(self.loginName)

        // but not another user's (here an admin fixture) -> 403
        val other = userRequests.retrieveByFilter("login_name", requireNotNull(TestFixtures.admin().loginName), ADMIN)
        assertThat(userRequests.retrieveByIdStatusCode(other.id!!, selfCredentials))
            .isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun `filtering users by login name as an admin returns the matching user`() {
        val created =
            userRequests
                .create(
                    listOf(newUserDto("filtered_user", "filtered.user@uni-heidelberg.de"))
                ).first()

        val filtered = userRequests.retrieveByFilter("login_name", created.loginName!!, ADMIN)

        assertEqualsIgnoringTimestamps(userDtoMapper.toDomain(filtered), userDtoMapper.toDomain(created))
    }

    @Test
    fun `a user may update their own profile but not another user's`() {
        // register a user and learn its credentials so it can authenticate as itself
        val password = "self-service-password"
        val selfDto = newUserDto("self_user", "self.user@uni-heidelberg.de").copy(password = password)
        val self = userRequests.create(listOf(selfDto)).first()
        val selfCredentials = Credentials(self.loginName!!, password)

        // editing their own profile succeeds
        val edited =
            userRequests
                .update(
                    listOf(self.copy(firstName = "Renamed", password = password)),
                    selfCredentials
                ).first()
        assertThat(edited.firstName).isEqualTo("Renamed")

        // editing another user is forbidden for a plain USER
        val other = userRequests.retrieveByFilter("login_name", requireNotNull(TestFixtures.admin().loginName), ADMIN)
        val statusCode =
            userRequests
                .updateAndReturnStatusCodes(
                    listOf(other.copy(firstName = "Hijacked", password = password)),
                    selfCredentials
                ).first()
        assertThat(statusCode).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun `a user setting their own roles is rejected while an admin may change roles`() {
        val password = "no-self-promotion"
        val selfDto = newUserDto("aspiring_admin", "aspiring.admin@uni-heidelberg.de").copy(password = password)
        val self = userRequests.create(listOf(selfDto)).first()
        val selfCredentials = Credentials(self.loginName!!, password)

        // a USER trying to grant itself ADMIN on its own account is forbidden
        val selfPromotion = self.copy(roles = setOf(Role.USER, Role.ADMIN), password = password)
        assertThat(userRequests.updateAndReturnStatusCodes(listOf(selfPromotion), selfCredentials).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())

        // an admin may change another user's roles
        val byAdmin =
            userRequests
                .update(
                    listOf(self.copy(roles = setOf(Role.USER, Role.MODERATOR), password = password)),
                    ADMIN
                ).first()
        assertThat(byAdmin.roles).containsExactlyInAnyOrder(Role.USER, Role.MODERATOR)
    }

    @Test
    fun `an admin may edit another user's profile fields`() {
        // an admin may edit any user; here it changes another user's name and email, not their roles
        val target =
            userRequests.create(listOf(newUserDto("edited_by_admin", "edited.by.admin@uni-heidelberg.de"))).first()

        val edited =
            userRequests
                .update(
                    listOf(target.copy(firstName = "Renamed", emailAddress = "renamed@uni-heidelberg.de")),
                    ADMIN
                ).first()

        assertThat(edited.firstName).isEqualTo("Renamed")
        assertThat(edited.emailAddress).isEqualTo("renamed@uni-heidelberg.de")
        // editing profile fields leaves the target's roles untouched
        assertThat(edited.roles).containsExactly(Role.USER)
    }

    @Test
    fun `a user may update their profile without resending the password`() {
        // the password is required only on registration; an update may omit it to keep the stored one
        val password = "keep-this-password"
        val selfDto = newUserDto("keeps_password", "keeps.password@uni-heidelberg.de").copy(password = password)
        val self = userRequests.create(listOf(selfDto)).first()
        val selfCredentials = Credentials(self.loginName!!, password)

        // the update omits the password and still succeeds
        val edited =
            userRequests
                .update(listOf(self.copy(firstName = "Renamed", password = null)), selfCredentials)
                .first()
        assertThat(edited.firstName).isEqualTo("Renamed")

        // the original password still authenticates, so it was kept rather than cleared
        val again =
            userRequests
                .update(listOf(edited.copy(lastName = "Again", password = null)), selfCredentials)
                .first()
        assertThat(again.lastName).isEqualTo("Again")
    }

    @Test
    fun `deleting a user is admin-only`() {
        val created = userRequests.create(listOf(newUserDto("to_delete", "to.delete@uni-heidelberg.de"))).first()

        // a plain USER cannot delete a user (admin-only by URL rule -> 403)
        assertThat(userRequests.deleteAndReturnStatusCodes(listOf(created.id!!), USER).first())
            .isEqualTo(HttpStatus.FORBIDDEN.value())

        // an admin can, and a repeat is a 404
        val statusCodes = userRequests.deleteAndReturnStatusCodes(listOf(created.id!!, created.id!!), ADMIN)
        assertThat(statusCodes).containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NOT_FOUND.value())

        val remainingIds = userRequests.retrieveAll(ADMIN).map { it.id }
        assertThat(remainingIds).doesNotContain(created.id)
    }

    // helpers ---------------------------------------------------------------------

    /** A registration DTO for a fresh, non-fixture user with a valid password. */
    private fun newUserDto(
        loginName: String,
        emailAddress: String
    ): UserDto =
        UserDto(
            loginName = loginName,
            emailAddress = emailAddress,
            firstName = "Fresh",
            lastName = "User",
            password = "valid-password-123"
        )
}
