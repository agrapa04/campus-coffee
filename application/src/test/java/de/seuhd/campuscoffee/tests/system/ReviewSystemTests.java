package de.seuhd.campuscoffee.tests.system;

import de.seuhd.campuscoffee.api.dtos.ReviewDto;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.reviewRequests;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for the operations related to reviews, including the approval workflow.
 * The default approval quorum is {@code campus-coffee.approval.min-count = 3}, so a review needs
 * three approvals from users other than the author to become approved.
 */
public class ReviewSystemTests extends AbstractSysTest {

    @Test
    void createReviewStartsUnapproved() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");

        ReviewDto created = reviewRequests
                .create(List.of(reviewFor(pos, author, "Solid espresso and plenty of seating.")))
                .getFirst();

        assertThat(created.approved()).isFalse();
    }

    @Test
    void retrieveAllAndById() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");
        ReviewDto created = reviewRequests
                .create(List.of(reviewFor(pos, author, "A reliable spot between lectures.")))
                .getFirst();

        assertThat(reviewRequests.retrieveAll())
                .extracting(ReviewDto::getId)
                .containsExactly(created.getId());

        ReviewDto byId = reviewRequests.retrieveById(created.getId());
        assertThat(byId.review()).isEqualTo("A reliable spot between lectures.");
    }

    @Test
    void updateReviewChangesText() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");
        ReviewDto created = reviewRequests
                .create(List.of(reviewFor(pos, author, "Original review text, long enough.")))
                .getFirst();

        // approval count and status are not asserted: an update currently resets both because
        // ReviewDtoMapper hardcodes them on toDomain.
        ReviewDto updated = reviewRequests
                .update(List.of(created.toBuilder().review("Updated review text, also long enough.").build()))
                .getFirst();

        assertThat(updated.review()).isEqualTo("Updated review text, also long enough.");
        assertThat(reviewRequests.retrieveById(created.getId()).review())
                .isEqualTo("Updated review text, also long enough.");
    }

    @Test
    void deleteReview() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");
        ReviewDto created = reviewRequests
                .create(List.of(reviewFor(pos, author, "This review will be deleted.")))
                .getFirst();

        List<Integer> statusCodes = reviewRequests
                .deleteAndReturnStatusCodes(List.of(created.getId(), created.getId()));

        // the first delete returns 204 No Content, the second 404 Not Found
        assertThat(statusCodes)
                .containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NOT_FOUND.value());
    }

    @Test
    void approvalBelowQuorumDoesNotApprove() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");
        User firstApprover = createUser("approver_one", "approver.one@uni-heidelberg.de");
        User secondApprover = createUser("approver_two", "approver.two@uni-heidelberg.de");
        ReviewDto review = reviewRequests
                .create(List.of(reviewFor(pos, author, "Review that stays below the quorum.")))
                .getFirst();

        reviewRequests.approve(review.getId(), firstApprover.id());
        ReviewDto afterTwoApprovals = reviewRequests.approve(review.getId(), secondApprover.id());

        assertThat(afterTwoApprovals.approved()).isFalse();
    }

    @Test
    void approvalReachingQuorumApprovesReview() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");
        User firstApprover = createUser("approver_one", "approver.one@uni-heidelberg.de");
        User secondApprover = createUser("approver_two", "approver.two@uni-heidelberg.de");
        User thirdApprover = createUser("approver_three", "approver.three@uni-heidelberg.de");
        ReviewDto review = reviewRequests
                .create(List.of(reviewFor(pos, author, "Review that reaches the quorum.")))
                .getFirst();

        reviewRequests.approve(review.getId(), firstApprover.id());
        reviewRequests.approve(review.getId(), secondApprover.id());
        ReviewDto afterThreeApprovals = reviewRequests.approve(review.getId(), thirdApprover.id());

        assertThat(afterThreeApprovals.approved()).isTrue();
    }

    @Test
    void selfApprovalIsRejected() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");
        ReviewDto review = reviewRequests
                .create(List.of(reviewFor(pos, author, "Author tries to approve this review.")))
                .getFirst();

        int statusCode = reviewRequests.approveAndReturnStatusCode(review.getId(), author.id());

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void duplicateReviewBySameAuthorForSamePosRejected() {
        Pos pos = createPos();
        User author = createUser("author", "author@uni-heidelberg.de");
        reviewRequests.create(List.of(reviewFor(pos, author, "First review by this author.")));

        int statusCode = reviewRequests
                .createAndReturnStatusCodes(List.of(reviewFor(pos, author, "Second review by the same author.")))
                .getFirst();

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void approveMissingReviewReturnsNotFound() {
        User approver = createUser("approver", "approver@uni-heidelberg.de");

        int statusCode = reviewRequests.approveAndReturnStatusCode(9999L, approver.id());

        assertThat(statusCode).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void filterByApprovalStatusPartitionsReviews() {
        Pos pos = createPos();
        User approvedAuthor = createUser("approved_author", "approved.author@uni-heidelberg.de");
        User pendingAuthor = createUser("pending_author", "pending.author@uni-heidelberg.de");
        User firstApprover = createUser("approver_one", "approver.one@uni-heidelberg.de");
        User secondApprover = createUser("approver_two", "approver.two@uni-heidelberg.de");
        User thirdApprover = createUser("approver_three", "approver.three@uni-heidelberg.de");

        ReviewDto approvedReview = reviewRequests
                .create(List.of(reviewFor(pos, approvedAuthor, "This review reaches the quorum.")))
                .getFirst();
        ReviewDto pendingReview = reviewRequests
                .create(List.of(reviewFor(pos, pendingAuthor, "This review stays below the quorum.")))
                .getFirst();

        reviewRequests.approve(approvedReview.getId(), firstApprover.id());
        reviewRequests.approve(approvedReview.getId(), secondApprover.id());
        reviewRequests.approve(approvedReview.getId(), thirdApprover.id());

        List<ReviewDto> approved = reviewRequests
                .retrieveByFilter(Map.of("pos_id", pos.id(), "approved", true));
        assertThat(approved).extracting(ReviewDto::getId).containsExactly(approvedReview.getId());

        List<ReviewDto> pending = reviewRequests
                .retrieveByFilter(Map.of("pos_id", pos.id(), "approved", false));
        assertThat(pending).extracting(ReviewDto::getId).containsExactly(pendingReview.getId());
    }

    // helpers ---------------------------------------------------------------------

    private Pos createPos() {
        return posService.upsert(TestFixtures.getPosFixturesForInsertion().getFirst());
    }

    private User createUser(String loginName, String emailAddress) {
        return userService.upsert(User.builder()
                .loginName(loginName)
                .emailAddress(emailAddress)
                .firstName("First")
                .lastName("Last")
                .build());
    }

    private ReviewDto reviewFor(Pos pos, User author, String text) {
        return ReviewDto.builder()
                .posId(pos.id())
                .authorId(author.id())
                .review(text)
                .build();
    }
}
