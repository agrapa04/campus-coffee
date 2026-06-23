package de.seuhd.campuscoffee.data.persistence.entities

import de.seuhd.campuscoffee.domain.model.objects.Role
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * Database entity for a registered user.
 */
@jakarta.persistence.Entity
@Table(name = "users")
class UserEntity : Entity() {
    @field:Column(name = LOGIN_NAME_COLUMN)
    var loginName: String? = null

    @field:Column(name = EMAIL_ADDRESS_COLUMN)
    var emailAddress: String? = null

    @field:Column(name = "first_name")
    var firstName: String? = null

    @field:Column(name = "last_name")
    var lastName: String? = null

    @field:Column(name = "password_hash")
    var passwordHash: String? = null

    @field:ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @field:CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @field:Column(name = "role")
    @field:Enumerated(EnumType.STRING)
    var roles: MutableSet<Role> = mutableSetOf()

    // Optimistic locking version; the losing side of a concurrent update returns 409 instead of silently
    // overwriting. Defaults to 0 (not null) across these entities so a detached copy built by the mapper is
    // read as detached, not transient: a null version reads as transient and breaks a @ManyToOne to it, such
    // as a review's reference to its POS and author.
    @field:Version
    @field:Column(name = "version")
    var version: Long? = 0

    companion object {
        const val LOGIN_NAME_COLUMN = "login_name"
        const val EMAIL_ADDRESS_COLUMN = "email_address"

        /** Names of the unique constraints, declared in the Flyway migration. */
        const val LOGIN_NAME_UNIQUE_CONSTRAINT = "uq_users_login_name"
        const val EMAIL_ADDRESS_UNIQUE_CONSTRAINT = "uq_users_email_address"
    }
}
