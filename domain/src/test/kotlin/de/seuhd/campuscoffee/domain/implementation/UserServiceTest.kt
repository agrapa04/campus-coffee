package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.ForbiddenException
import de.seuhd.campuscoffee.domain.model.objects.Role
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.data.PasswordHasher
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [UserServiceImpl], which delegates to the [UserDataService] port: the login-name
 * lookup and the inherited id lookup must both resolve through that port, and a freshly supplied raw
 * password is hashed via the [PasswordHasher] port before it is persisted.
 */
@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    @Mock
    private lateinit var userDataService: UserDataService

    @Mock
    private lateinit var passwordHasher: PasswordHasher

    private lateinit var userService: UserServiceImpl

    @BeforeEach
    fun setUp() {
        userService = UserServiceImpl(userDataService, passwordHasher)
    }

    @Test
    fun `getByLoginName returns the user resolved by the data service`() {
        val user = TestFixtures.getUserFixtures().first()
        whenever(userDataService.getByLoginName(user.loginName)).thenReturn(user)

        assertThat(userService.getByLoginName(user.loginName)).isEqualTo(user)
        verify(userDataService).getByLoginName(user.loginName)
    }

    @Test
    fun `getById returns the user resolved by the data service`() {
        // also pins that the service exposes the injected port (a null port would fail this lookup)
        val user = TestFixtures.getUserFixtures().first()
        val id = requireNotNull(user.id)
        whenever(userDataService.getById(id)).thenReturn(user)

        assertThat(userService.getById(id)).isEqualTo(user)
        verify(userDataService).getById(id)
    }

    @Test
    fun `getById with an acting user returns the target when the caller is that same user`() {
        val user = TestFixtures.plainUser()
        val id = requireNotNull(user.id)
        whenever(userDataService.getById(id)).thenReturn(user)

        assertThat(userService.getById(id, actingUser = user)).isEqualTo(user)
    }

    @Test
    fun `getById with an acting user returns any user when the caller is an admin`() {
        val target = TestFixtures.plainUser()
        val admin = TestFixtures.admin()
        val id = requireNotNull(target.id)
        whenever(userDataService.getById(id)).thenReturn(target)

        assertThat(userService.getById(id, actingUser = admin)).isEqualTo(target)
    }

    @Test
    fun `getById with an acting user throws ForbiddenException when a non-admin reads another user`() {
        val target = TestFixtures.admin()
        val actor = TestFixtures.plainUser()
        val id = requireNotNull(target.id)
        whenever(userDataService.getById(id)).thenReturn(target)

        assertThrows<ForbiddenException> { userService.getById(id, actingUser = actor) }
    }

    @Test
    fun `getByLoginName with an acting user throws ForbiddenException when a non-admin reads another user`() {
        val target = TestFixtures.admin()
        val actor = TestFixtures.plainUser()
        whenever(userDataService.getByLoginName(target.loginName)).thenReturn(target)

        assertThrows<ForbiddenException> { userService.getByLoginName(target.loginName, actingUser = actor) }
    }

    @Test
    fun `upsert hashes a supplied raw password and clears it before persisting`() {
        val user = TestFixtures.getUserFixturesForInsertion().first().copy(password = "plaintext1")
        whenever(passwordHasher.hash("plaintext1")).thenReturn("{bcrypt}HASHED")
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.upsert(user)

        // the raw password is hashed and nulled out, so it is never persisted or returned
        assertThat(result.passwordHash).isEqualTo("{bcrypt}HASHED")
        assertThat(result.password).isNull()
        verify(passwordHasher).hash("plaintext1")
    }

    @Test
    fun `upsert without a raw password does not invoke the hasher`() {
        val user = TestFixtures.getUserFixturesForInsertion().first().copy(password = null)
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.upsert(user)

        assertThat(result.passwordHash).isNull()
        verify(passwordHasher, never()).hash(any())
    }

    @Test
    fun `upsert without a raw password keeps the stored hash on update`() {
        val existing = TestFixtures.getUserFixtures().first().copy(passwordHash = "{bcrypt}STORED")
        val update = existing.copy(password = null, passwordHash = null, firstName = "Janet")
        whenever(userDataService.getById(existing.id!!)).thenReturn(existing)
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.upsert(update)

        // the omitted password leaves the stored hash intact instead of nulling it
        assertThat(result.passwordHash).isEqualTo("{bcrypt}STORED")
        verify(passwordHasher, never()).hash(any())
    }

    @Test
    fun `register forces a plain USER role regardless of the requested roles`() {
        // a client trying to self-assign ADMIN on registration still ends up a plain USER
        val requested =
            TestFixtures
                .getUserFixturesForInsertion()
                .first()
                .copy(password = "longenough1", roles = setOf(Role.ADMIN, Role.MODERATOR))
        whenever(passwordHasher.hash(any())).thenReturn("{bcrypt}HASHED")
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.register(requested)

        assertThat(result.roles).containsExactly(Role.USER)
    }

    @Test
    fun `update lets a user edit their own profile but keeps their roles`() {
        val existing = TestFixtures.moderator()
        val id = requireNotNull(existing.id)
        // the user edits their own name and (uselessly) omits roles; the existing roles must survive
        val update = existing.copy(firstName = "Maximilian", roles = emptySet(), passwordHash = null)
        whenever(userDataService.getById(id)).thenReturn(existing)
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.update(update, actingUser = existing)

        assertThat(result.firstName).isEqualTo("Maximilian")
        assertThat(result.roles).isEqualTo(existing.roles)
    }

    @Test
    fun `update throws ForbiddenException when a non-admin edits another user`() {
        val target = TestFixtures.admin()
        val actor = TestFixtures.plainUser()
        val update = target.copy(firstName = "Hijacked", passwordHash = null)

        assertThrows<ForbiddenException> { userService.update(update, actingUser = actor) }
        verify(userDataService, never()).upsert(any())
    }

    @Test
    fun `update throws ForbiddenException when a non-admin changes their own roles`() {
        val self = TestFixtures.plainUser()
        val id = requireNotNull(self.id)
        // the user tries to grant themselves ADMIN on their own account
        val update = self.copy(roles = setOf(Role.USER, Role.ADMIN), passwordHash = null)
        whenever(userDataService.getById(id)).thenReturn(self)

        assertThrows<ForbiddenException> { userService.update(update, actingUser = self) }
        verify(userDataService, never()).upsert(any())
    }

    @Test
    fun `update lets an admin change another user's roles`() {
        val admin = TestFixtures.admin()
        val target = TestFixtures.plainUser()
        val id = requireNotNull(target.id)
        val update = target.copy(roles = setOf(Role.USER, Role.MODERATOR), passwordHash = null)
        whenever(userDataService.getById(id)).thenReturn(target)
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.update(update, actingUser = admin)

        assertThat(result.roles).containsExactlyInAnyOrder(Role.USER, Role.MODERATOR)
    }

    @Test
    fun `update keeps the USER role when an admin sets a role set without USER`() {
        val admin = TestFixtures.admin()
        val target = TestFixtures.plainUser()
        val id = requireNotNull(target.id)
        // an admin tries to set roles to MODERATOR only, dropping the base USER role
        val update = target.copy(roles = setOf(Role.MODERATOR), passwordHash = null)
        whenever(userDataService.getById(id)).thenReturn(target)
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.update(update, actingUser = admin)

        // USER is the base role and is always retained, so the result holds both
        assertThat(result.roles).containsExactlyInAnyOrder(Role.USER, Role.MODERATOR)
    }

    @Test
    fun `update lets an admin change their own roles`() {
        val admin = TestFixtures.admin()
        val id = requireNotNull(admin.id)
        // changing roles is an admin action, including on one's own account
        val update = admin.copy(roles = setOf(Role.USER, Role.ADMIN), passwordHash = null)
        whenever(userDataService.getById(id)).thenReturn(admin)
        whenever(userDataService.upsert(any())).thenAnswer { it.arguments[0] as User }

        val result = userService.update(update, actingUser = admin)

        assertThat(result.roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
    }
}
