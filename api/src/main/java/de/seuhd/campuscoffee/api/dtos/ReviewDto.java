package de.seuhd.campuscoffee.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * DTO record for POS metadata.
 */
@Builder(toBuilder = true)
public record ReviewDto (
    @Nullable Long id,
    @Nullable LocalDateTime createdAt,
    @Nullable LocalDateTime updatedAt,

    @NotNull(message = "POS ID cannot be null.")
    @NonNull Long posId,

    @NotNull(message = "Author ID cannot be null.")
    @NonNull Long authorId,

    @NotBlank(message = "Review text cannot be empty.")
    @Size(min = MIN_REVIEW_LENGTH, max = MAX_REVIEW_LENGTH,
            message = "Review must be between {min} and {max} characters long.")

    @NonNull String review,

    @Nullable Boolean approved // missing when creating a new review
) implements Dto<Long> {
    /**
     * Inclusive bounds on the review text length, used by the {@code @Size} constraint above. They are
     * part of the API contract: bean validation enforces them and springdoc surfaces them as
     * {@code minLength}/{@code maxLength} in the OpenAPI schema.
     */
    private static final int MIN_REVIEW_LENGTH = 10;
    private static final int MAX_REVIEW_LENGTH = 5000;

    @Override
    public @Nullable Long getId() {
        return id;
    }
}
