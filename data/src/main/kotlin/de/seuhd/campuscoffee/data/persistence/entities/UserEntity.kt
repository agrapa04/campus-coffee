package de.seuhd.campuscoffee.data.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Table

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

    companion object {
        const val LOGIN_NAME_COLUMN = "login_name"
        const val EMAIL_ADDRESS_COLUMN = "email_address"

        /** Names of the unique constraints, declared in the Flyway migration. */
        const val LOGIN_NAME_UNIQUE_CONSTRAINT = "uq_users_login_name"
        const val EMAIL_ADDRESS_UNIQUE_CONSTRAINT = "uq_users_email_address"
    }
}
