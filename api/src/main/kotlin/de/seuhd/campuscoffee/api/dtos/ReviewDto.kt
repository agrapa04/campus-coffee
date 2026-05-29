package de.seuhd.campuscoffee.api.dtos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * Inclusive bounds on the review text length, used by the `@Size` constraint below. They are part of
 * the API contract: bean validation enforces them and springdoc surfaces them as minLength/maxLength.
 */
private const val MIN_REVIEW_LENGTH = 10
private const val MAX_REVIEW_LENGTH = 5000

/**
 * DTO for a review. Properties are nullable so a request body that omits a field deserializes and is
 * then rejected by bean validation; the controller validates the DTO before it is mapped to a [Review].
 */
data class ReviewDto(
    override val id: Long? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,

    @field:NotNull(message = "POS ID cannot be null.")
    val posId: Long?,

    @field:NotNull(message = "Author ID cannot be null.")
    val authorId: Long?,

    @field:NotBlank(message = "Review text cannot be empty.")
    @field:Size(
        min = MIN_REVIEW_LENGTH, max = MAX_REVIEW_LENGTH,
        message = "Review must be between {min} and {max} characters long.",
    )
    val review: String?,

    val approved: Boolean? = null, // missing when creating a new review
) : Dto<Long> {

    // --- temporary bridges so the still-Java mapper and tests keep compiling; removed once they are Kotlin ---
    fun id() = id
    fun createdAt() = createdAt
    fun updatedAt() = updatedAt
    fun posId() = posId
    fun authorId() = authorId
    fun review() = review
    fun approved() = approved

    fun toBuilder() = Builder()
        .id(id).createdAt(createdAt).updatedAt(updatedAt)
        .posId(posId).authorId(authorId).review(review).approved(approved)

    class Builder {
        private var id: Long? = null
        private var createdAt: LocalDateTime? = null
        private var updatedAt: LocalDateTime? = null
        private var posId: Long? = null
        private var authorId: Long? = null
        private var review: String? = null
        private var approved: Boolean? = null

        fun id(v: Long?) = apply { id = v }
        fun createdAt(v: LocalDateTime?) = apply { createdAt = v }
        fun updatedAt(v: LocalDateTime?) = apply { updatedAt = v }
        fun posId(v: Long?) = apply { posId = v }
        fun authorId(v: Long?) = apply { authorId = v }
        fun review(v: String?) = apply { review = v }
        fun approved(v: Boolean?) = apply { approved = v }

        fun build() = ReviewDto(id, createdAt, updatedAt, posId, authorId, review, approved)
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
