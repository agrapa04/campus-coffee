package de.seuhd.campuscoffee.domain.model.objects

import java.time.LocalDateTime

/**
 * Immutable user domain model. Fields are validated in the API layer via the DTOs.
 */
data class User(
    override val id: Long? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val loginName: String,
    val emailAddress: String,
    val firstName: String,
    val lastName: String,
) : DomainModel<Long> {

    // --- temporary bridges so the still-Java tests keep compiling; removed once the tests are Kotlin ---
    fun id() = id
    fun createdAt() = createdAt
    fun updatedAt() = updatedAt
    fun loginName() = loginName
    fun emailAddress() = emailAddress
    fun firstName() = firstName
    fun lastName() = lastName

    fun toBuilder() = Builder()
        .id(id).createdAt(createdAt).updatedAt(updatedAt)
        .loginName(loginName).emailAddress(emailAddress).firstName(firstName).lastName(lastName)

    class Builder {
        private var id: Long? = null
        private var createdAt: LocalDateTime? = null
        private var updatedAt: LocalDateTime? = null
        private var loginName: String? = null
        private var emailAddress: String? = null
        private var firstName: String? = null
        private var lastName: String? = null

        fun id(v: Long?) = apply { id = v }
        fun createdAt(v: LocalDateTime?) = apply { createdAt = v }
        fun updatedAt(v: LocalDateTime?) = apply { updatedAt = v }
        fun loginName(v: String) = apply { loginName = v }
        fun emailAddress(v: String) = apply { emailAddress = v }
        fun firstName(v: String) = apply { firstName = v }
        fun lastName(v: String) = apply { lastName = v }

        fun build() = User(id, createdAt, updatedAt, loginName!!, emailAddress!!, firstName!!, lastName!!)
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
