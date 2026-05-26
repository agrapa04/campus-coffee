package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration;
import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.api.ReviewService;
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the Review service that handles business logic related to review entities.
 */
@Slf4j
@Service
public class ReviewServiceImpl extends CrudServiceImpl<Review, Long> implements ReviewService {
    private final ReviewDataService reviewDataService;
    private final UserDataService userDataService;
    private final PosDataService posDataService;
    private final ApprovalConfiguration approvalConfiguration;

    public ReviewServiceImpl(@NonNull ReviewDataService reviewDataService,
                             @NonNull UserDataService userDataService,
                             @NonNull PosDataService posDataService,
                             @NonNull ApprovalConfiguration approvalConfiguration) {
        super(Review.class);
        this.reviewDataService = reviewDataService;
        this.userDataService = userDataService;
        this.posDataService = posDataService;
        this.approvalConfiguration = approvalConfiguration;
    }

    @Override
    protected CrudDataService<Review, Long> dataService() {
        return reviewDataService;
    }

    @Override
    @Transactional
    public @NonNull Review upsert(@NonNull Review review) {
        // validate that the POS exists before creating or updating the review
        Objects.requireNonNull(review.pos().getId());
        Pos pos = posDataService.getById(review.pos().getId());

        // on creation, validate that this is the author's first review for this POS;
        // on update the filter would match the review being updated, so skip the check
        if (review.getId() == null && !reviewDataService.filter(pos, review.author()).isEmpty()) {
            throw new ValidationException("Author with ID '" + review.author().getId()
                    + " has already reviewed POS with ID '" + pos.getId() + "'.");
        }

        return super.upsert(review);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Review> filter(@NonNull Long posId, @NonNull Boolean approved) {
        return reviewDataService.filter(posDataService.getById(posId), approved);
    }

    @Override
    @Transactional
    public @NonNull Review approve(@NonNull Review review, @NonNull Long userId) {
        log.info("Processing approval request for review with ID '{}' by user with ID '{}'...",
                review.getId(), userId);

        // validate that the user exists
        User user = userDataService.getById(userId);
        Objects.requireNonNull(user.getId());

        // validate that the review exists
        Objects.requireNonNull(review.getId());
        Review reviewToApprove = reviewDataService.getById(review.getId());
        Objects.requireNonNull(reviewToApprove.author().getId());

        // a user cannot approve their own review
        if (reviewToApprove.author().getId().equals(user.getId())) {
            log.warn("User with ID '{}' attempted to approve their own review with ID '{}'.",
                    user.getId(), review.getId());
            throw new ValidationException("User with ID '" + user.getId()
                    + "' cannot approve their own review with ID '" + review.getId() + "'.");
        }

        // increment approval count
        Review approvedReview = review.toBuilder().approvalCount(reviewToApprove.approvalCount() + 1).build();

        // update approval status to determine if the review now reaches the approval quorum
        Review finalReview = updateApprovalStatus(approvedReview);
        if (finalReview.approved()) {
            log.info("Review with ID '{}' has now reached the approval quorum ({}/{})",
                    finalReview.getId(),
                    finalReview.approvalCount(),
                    approvalConfiguration.minCount());
        } else {
            log.info("Review with ID '{}' has not reached the approval quorum ({}/{})",
                    finalReview.getId(),
                    finalReview.approvalCount(),
                    approvalConfiguration.minCount());
        }

        return reviewDataService.upsert(finalReview);
    }

    /**
     * Calculates and updates the approval status of a review based on the approval count.
     * Business rule: A review is approved when it reaches the configured minimum approval count threshold.
     *
     * @param review The review to calculate approval status for
     * @return The review with updated approval status
     */
    Review updateApprovalStatus(Review review) {
        log.debug("Updating approval status of review with ID '{}'...", review.getId());
        return review.toBuilder()
                .approved(isApproved(review))
                .build();
    }
    
    /**
     * Determines if a review meets the minimum approval threshold.
     * 
     * @param review The review to check
     * @return true if the review meets or exceeds the minimum approval count, false otherwise
     */
    private boolean isApproved(Review review) {
        return review.approvalCount() >= approvalConfiguration.minCount();
    }
}
