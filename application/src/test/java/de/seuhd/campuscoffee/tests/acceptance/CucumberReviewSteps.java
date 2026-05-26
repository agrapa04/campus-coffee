package de.seuhd.campuscoffee.tests.acceptance;

import de.seuhd.campuscoffee.api.dtos.PosDto;
import de.seuhd.campuscoffee.api.dtos.ReviewDto;
import de.seuhd.campuscoffee.api.dtos.UserDto;
import de.seuhd.campuscoffee.api.mapper.PosDtoMapper;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import io.cucumber.java.DataTableType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.posRequests;
import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.reviewRequests;
import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.userRequests;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for the review approval Cucumber tests. The Spring context, container, and
 * cleanup hooks live in {@link CucumberSpringConfiguration}.
 */
public class CucumberReviewSteps {

    @Autowired
    protected PosDtoMapper posDtoMapper;

    private final Map<String, UserDto> usersByLogin = new HashMap<>();
    private final Map<String, PosDto> posByName = new HashMap<>();
    private final Map<String, ReviewDto> reviewsByAuthorAndPos = new HashMap<>();
    private Integer lastApprovalStatusCode;

    /**
     * Register a Cucumber DataTable type for UserDto (distinct return type from
     * {@link CucumberPosSteps#toPosDto}, so there is no registration clash).
     *
     * @param row the DataTable row to map to a UserDto object
     * @return the mapped UserDto object
     */
    @DataTableType
    @SuppressWarnings("unused")
    public UserDto toUserDto(Map<String, String> row) {
        return UserDto.builder()
                .loginName(row.get("loginName"))
                .emailAddress(row.get("emailAddress"))
                .firstName(row.get("firstName"))
                .lastName(row.get("lastName"))
                .build();
    }

    // Given -----------------------------------------------------------------------

    @Given("the following users exist:")
    public void theFollowingUsersExist(List<UserDto> users) {
        userRequests.create(users).forEach(user -> usersByLogin.put(user.loginName(), user));
    }

    @Given("a POS named {string} exists")
    public void aPosNamedExists(String name) {
        PosDto pos = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().getFirst())
                .toBuilder().name(name).build();
        posByName.put(name, posRequests.create(List.of(pos)).getFirst());
    }

    @Given("{string} reviewed {string} with {string}")
    public void userReviewedPosWith(String login, String posName, String text) {
        createReview(login, posName, text);
    }

    // When -----------------------------------------------------------------------

    @When("{string} reviews {string} with {string}")
    public void userReviewsPosWith(String login, String posName, String text) {
        createReview(login, posName, text);
    }

    @When("{string} approves the review by {string} for {string}")
    public void userApprovesReview(String approverLogin, String authorLogin, String posName) {
        ReviewDto review = reviewsByAuthorAndPos.get(reviewKey(authorLogin, posName));
        ReviewDto updated = reviewRequests.approve(review.getId(), usersByLogin.get(approverLogin).getId());
        reviewsByAuthorAndPos.put(reviewKey(authorLogin, posName), updated);
    }

    @When("{string} tries to approve the review by {string} for {string}")
    public void userTriesToApproveReview(String approverLogin, String authorLogin, String posName) {
        ReviewDto review = reviewsByAuthorAndPos.get(reviewKey(authorLogin, posName));
        lastApprovalStatusCode = reviewRequests
                .approveAndReturnStatusCode(review.getId(), usersByLogin.get(approverLogin).getId());
    }

    // Then -----------------------------------------------------------------------

    @Then("the review by {string} for {string} is approved")
    public void theReviewIsApproved(String authorLogin, String posName) {
        assertThat(currentReview(authorLogin, posName).approved()).isTrue();
    }

    @Then("the review by {string} for {string} is not approved")
    public void theReviewIsNotApproved(String authorLogin, String posName) {
        assertThat(currentReview(authorLogin, posName).approved()).isFalse();
    }

    @Then("the approval is rejected")
    public void theApprovalIsRejected() {
        assertThat(lastApprovalStatusCode).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    // helpers ---------------------------------------------------------------------

    private void createReview(String login, String posName, String text) {
        ReviewDto review = ReviewDto.builder()
                .posId(posByName.get(posName).getId())
                .authorId(usersByLogin.get(login).getId())
                .review(text)
                .build();
        reviewsByAuthorAndPos.put(reviewKey(login, posName), reviewRequests.create(List.of(review)).getFirst());
    }

    private ReviewDto currentReview(String authorLogin, String posName) {
        return reviewRequests.retrieveById(reviewsByAuthorAndPos.get(reviewKey(authorLogin, posName)).getId());
    }

    private String reviewKey(String login, String posName) {
        return login + " @ " + posName;
    }
}
