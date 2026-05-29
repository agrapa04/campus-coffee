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
        min = MIN_REVIEW_LENGTH,
        max = MAX_REVIEW_LENGTH,
        message = "Review must be between {min} and {max} characters long."
    )
    val review: String?,
    // missing when creating a new review
    val approved: Boolean? = null
) : Dto<Long>
