package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;

import static de.seuhd.campuscoffee.domain.tests.TestFixtures.getApprovalConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit and integration tests for the operations related to reviews.
 */
@ExtendWith(MockitoExtension.class)
public class ReviewServiceTest {
    private final ApprovalConfiguration approvalConfiguration = getApprovalConfiguration();

    @Mock
    private ReviewDataService reviewDataService;

    @Mock
    private UserDataService userDataService;

    @Mock
    private PosDataService posDataService;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void beforeEach() {
        reviewService = new ReviewServiceImpl(
                reviewDataService, userDataService, posDataService, approvalConfiguration
        );
    }

    /**
     * Verifies a user cannot approve their own review. The test verifies that when the author
     * attempts to approve their own review, an exception is thrown.
     */
    @Test
    void approvalFailsIfUserIsAuthor() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        assertNotNull(review.author().id());
        when(userDataService.getById(review.author().id())).thenReturn(review.author());
        assertNotNull(review.id());
        when(reviewDataService.getById(review.id())).thenReturn(review);

        // when, then
        assertThrows(ValidationException.class, () -> reviewService.approve(review, review.author().getId()));
        verify(userDataService).getById(review.author().id());
        verify(reviewDataService).getById(review.getId());
    }

    /**
     * Verifies that the approval succeeds if the approver is not the author.
     * The test verifies that a valid approval increments the approval count
     * and sets the status of the review to "approved" if the required threshold is met.
     */
    @Test
    void approvalSuccessfulIfUserIsNotAuthor() {
        // given
        // one short of the quorum, so the single approval below pushes it to exactly the quorum
        Review review = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(approvalConfiguration.minCount() - 1)
                .approved(false)
                .build();
        User user = TestFixtures.getUserFixtures().getLast();
        assertNotNull(user.getId());
        when(userDataService.getById(user.getId())).thenReturn(user);
        assertNotNull(review.getId());
        when(reviewDataService.getById(review.getId())).thenReturn(review);
        when(reviewDataService.upsert(any(Review.class))).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        // when
        Review approvedReview = reviewService.approve(review, user.getId());

        // then
        verify(userDataService).getById(user.getId());
        verify(reviewDataService).getById(review.getId());
        verify(reviewDataService).upsert(any(Review.class));
        assertThat(approvedReview.approvalCount()).isEqualTo(review.approvalCount() + 1);
        assertThat(approvedReview.approved()).isTrue();
    }

    /**
     * Verifies that the service returns all approved reviews for a given POS.
     */
    @Test
    void retrieveAllApprovedPos() {
        // given
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        assertNotNull(pos.getId());
        List<Review> reviews = TestFixtures.getReviewFixtures().stream()
                .map(review -> review.toBuilder()
                        .pos(pos)
                        .approvalCount(approvalConfiguration.minCount())
                        .approved(true)
                        .build())
                .toList();
        when(posDataService.getById(pos.getId())).thenReturn(pos);
        when(reviewDataService.filter(pos, true)).thenReturn(reviews);

        // when
        List<Review> retrievedReviews = reviewService.filter(Objects.requireNonNull(pos.getId()), true);

        // then
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, true);
        assertThat(retrievedReviews).hasSize(reviews.size());
    }

    /**
     * Verifies that an exception is thrown when attempting to create or update a review
     * for a POS that does not exist in the system.
     */
    @Test
    void createReviewPosDoesNotExistException() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        assertNotNull(review.pos().getId());
        when(posDataService.getById(review.pos().getId())).thenThrow(
                new NotFoundException(review.pos().getClass(), review.pos().getId())
        );

        // when, then
        assertThrows(NotFoundException.class, () -> reviewService.upsert(review));
        verify(posDataService).getById(review.pos().getId());
    }

    /**
     * Verifies that a user cannot create more than one review for the same POS.
     * If an existing review for the same POS by the same author is found, the upsert should fail
     * with a validation error.
     */
    @Test
    void userCannotCreateMoreThanOneReviewPerPos() {
        // given a new review (id is null), since the duplicate check runs only on creation
        Review review = TestFixtures.getReviewFeaturesForInsertion().getFirst();
        Pos pos = review.pos();
        User author = review.author();
        assertNotNull(pos.getId());

        when(posDataService.getById(pos.getId())).thenReturn(pos);
        when(reviewDataService.filter(pos, author)).thenReturn(List.of(review));

        // when, then
        assertThrows(ValidationException.class, () -> reviewService.upsert(review));
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, author);
    }

    /**
     * Verifies that the approval status of a previously unapproved review is updated correctly
     * if it reaches the configured approval threshold.
     */
    @Test
    void approvalApprovesReviewWhenThresholdIsReached() {
        // given
        Review unapprovedReview = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(approvalConfiguration.minCount()-1)
                .approved(false)
                .build();

        // when
        Review updatedReview = reviewService.updateApprovalStatus(unapprovedReview);

        // then
        assertFalse(updatedReview.approved());

        // when
        Review approvedReview = unapprovedReview.toBuilder()
                .approvalCount(approvalConfiguration.minCount())
                .build();
        updatedReview = reviewService.updateApprovalStatus(approvedReview);

        // then
        assertTrue(updatedReview.approved());
    }

    /**
     * Verifies that the approval increments the count but does not mark the review as approved
     * when the approval quorum has not yet been reached.
     */
    @Test
    void approvalDoesNotApproveReviewWhenThresholdIsNotReached() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(0)
                .approved(false)
                .build();
        assertNotNull(review.getId());
        User user = TestFixtures.getUserFixtures().getLast();
        assertNotNull(user.getId());

        when(userDataService.getById(user.getId())).thenReturn(user); // user exists
        when(reviewDataService.getById(review.getId())).thenReturn(review); // review exists
        when(reviewDataService.upsert(any(Review.class))).thenAnswer(
                invocation -> invocation.getArgument(0) // return the updated review
        );

        // when
        Review approvedReview = reviewService.approve(review, user.getId());

        // then
        verify(userDataService).getById(user.getId());
        verify(reviewDataService).getById(review.getId());
        verify(reviewDataService).upsert(any(Review.class));
        assertThat(approvedReview.approvalCount()).isEqualTo(1);
        assertThat(approvedReview.approved()).isFalse();
    }

    /**
     * Verifies that a review can be created successfully for a POS that exists
     * and for which the author has not already provided a review.
     * This covers the happy path in the review creation (previously uncovered).
     */
    @Test
    void reviewCreationSuccessfulForExistingPosAndAuthorWithoutPreviousReview() {
        // given
        Review review = TestFixtures.getReviewFeaturesForInsertion().getFirst();
        Pos pos = review.pos();
        assertNotNull(pos.getId());

        when(posDataService.getById(pos.getId())).thenReturn(pos); // POS exists
        when(reviewDataService.filter(pos, review.author())).thenReturn(List.of()); // no review exists for this POS by this author
        when(reviewDataService.upsert(review)).thenReturn(review); // the data service returns the test fixture

        // when
        Review result = reviewService.upsert(review);

        // then
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, review.author());
        verify(reviewDataService).upsert(review);
        assertThat(result.getId()).isEqualTo(review.getId());
    }

    /**
     * Verifies that updating an existing review skips the check that runs on creation, which rejects
     * a second review by the same author for the same POS. On update that filter would match the
     * review being updated and previously made every update fail with a validation error.
     */
    @Test
    void updatingExistingReviewSkipsDuplicateCheck() {
        // given an existing review with a non-null id
        Review review = TestFixtures.getReviewFixtures().getFirst();
        Pos pos = review.pos();
        assertNotNull(review.getId());
        assertNotNull(pos.getId());

        when(posDataService.getById(pos.getId())).thenReturn(pos);
        when(reviewDataService.getById(review.getId())).thenReturn(review); // entity exists for the update
        when(reviewDataService.upsert(review)).thenReturn(review);

        // when
        Review result = reviewService.upsert(review);

        // then the per-author filter is never consulted on update
        verify(reviewDataService, never()).filter(any(Pos.class), any(User.class));
        verify(reviewDataService).upsert(review);
        assertThat(result.getId()).isEqualTo(review.getId());
    }
}
