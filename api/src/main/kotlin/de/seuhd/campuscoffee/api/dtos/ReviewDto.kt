package de.seuhd.campuscoffee.api.dtos

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

/**
 * Inclusive bounds on the review text length, used by the `@Size` constraint below. They are part of
 * the API contract: bean validation enforces them and springdoc surfaces them as minLength/maxLength.
 */
private const val MIN_REVIEW_LENGTH = 10
private const val MAX_REVIEW_LENGTH = 5000

/**
 * DTO for a review. Properties are nullable, so a request body that omits a field deserializes and is
 * then rejected by bean validation; the controller validates the DTO before it is mapped to a
 * [de.seuhd.campuscoffee.domain.model.objects.Review].
 *
 * [authorId] is read-only. It appears in responses, but the author of a created review is taken from
 * the authenticated user, never from the request body. A `POST` carrying an [authorId] is rejected with
 * 400, exactly as a client-supplied [id] is, so a client cannot post a review as someone else.
 */
data class ReviewDto(
    override val id: UUID? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    @field:Schema(accessMode = Schema.AccessMode.READ_ONLY)
    val version: Long? = null,
    @field:NotNull(message = "POS ID cannot be null.")
    val posId: UUID?,
    @field:Schema(accessMode = Schema.AccessMode.READ_ONLY)
    val authorId: UUID? = null,
    @field:NotBlank(message = "Review text cannot be empty.")
    @field:Size(
        min = MIN_REVIEW_LENGTH,
        max = MAX_REVIEW_LENGTH,
        message = "Review must be between {min} and {max} characters long."
    )
    val review: String?,
    // missing when creating a new review
    val approved: Boolean? = null
) : Dto<UUID>
