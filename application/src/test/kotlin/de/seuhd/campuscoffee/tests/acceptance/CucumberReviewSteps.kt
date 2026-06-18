package de.seuhd.campuscoffee.tests.acceptance

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.api.dtos.UserDto
import de.seuhd.campuscoffee.api.mapper.PosDtoMapper
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.Credentials
import de.seuhd.campuscoffee.tests.SystemTestUtils.MODERATOR
import de.seuhd.campuscoffee.tests.SystemTestUtils.posRequests
import de.seuhd.campuscoffee.tests.SystemTestUtils.reviewRequests
import de.seuhd.campuscoffee.tests.SystemTestUtils.userRequests
import io.cucumber.java.DataTableType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpStatus

/**
 * Step definitions for the review approval Cucumber tests. The Spring context, container, and cleanup
 * hooks live in [CucumberSpringConfiguration].
 *
 * Every write request authenticates. The feature's users are registered with a known password (the Background
 * table carries it), and the steps remember each login's credentials so a review is created as its named
 * author and each approval is recorded under the approver's own login.
 */
class CucumberReviewSteps(
    private val posDtoMapper: PosDtoMapper
) {
    private val credentialsByLogin = mutableMapOf<String, Credentials>()
    private val posByName = mutableMapOf<String, PosDto>()
    private val reviewsByAuthorAndPos = mutableMapOf<String, ReviewDto>()
    private var lastApprovalStatusCode = 0

    /**
     * Register a Cucumber DataTable type for [UserDto] (distinct return type from
     * [CucumberPosSteps.toPosDto], so there is no registration clash). The `password` column carries the
     * known password the steps later authenticate with.
     *
     * @param row the DataTable row to map to a [UserDto]
     * @return the mapped [UserDto]
     */
    @DataTableType
    @Suppress("unused")
    fun toUserDto(row: Map<String, String>): UserDto =
        UserDto(
            loginName = row["loginName"],
            emailAddress = row["emailAddress"],
            firstName = row["firstName"],
            lastName = row["lastName"],
            password = row["password"]
        )

    // Given -----------------------------------------------------------------------

    @Given("the following users exist:")
    fun theFollowingUsersExist(users: List<UserDto>) {
        userRequests.create(users).forEachIndexed { index, created ->
            // the response never carries the password, so pair the login with the one from the table row
            credentialsByLogin[created.loginName!!] = Credentials(created.loginName!!, users[index].password!!)
        }
    }

    @Given("a POS named {string} exists")
    fun aPosNamedExists(name: String) {
        val pos = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first()).copy(name = name)
        // creating a POS requires a moderator
        posByName[name] = posRequests.create(listOf(pos), MODERATOR).first()
    }

    @Given("{string} reviewed {string} with {string}")
    fun userReviewedPosWith(
        login: String,
        posName: String,
        text: String
    ) {
        createReview(login, posName, text)
    }

    // When -----------------------------------------------------------------------

    @When("{string} reviews {string} with {string}")
    fun userReviewsPosWith(
        login: String,
        posName: String,
        text: String
    ) {
        createReview(login, posName, text)
    }

    @When("{string} approves the review by {string} for {string}")
    fun userApprovesReview(
        approverLogin: String,
        authorLogin: String,
        posName: String
    ) {
        val review = reviewsByAuthorAndPos.getValue(reviewKey(authorLogin, posName))
        val updated = reviewRequests.approve(review.id!!, credentialsByLogin.getValue(approverLogin))
        reviewsByAuthorAndPos[reviewKey(authorLogin, posName)] = updated
    }

    @When("{string} tries to approve the review by {string} for {string}")
    fun userTriesToApproveReview(
        approverLogin: String,
        authorLogin: String,
        posName: String
    ) {
        val review = reviewsByAuthorAndPos.getValue(reviewKey(authorLogin, posName))
        lastApprovalStatusCode =
            reviewRequests.approveAndReturnStatusCode(review.id!!, credentialsByLogin.getValue(approverLogin))
    }

    // Then -----------------------------------------------------------------------

    @Then("the review by {string} for {string} is approved")
    fun theReviewIsApproved(
        authorLogin: String,
        posName: String
    ) {
        assertThat(currentReview(authorLogin, posName).approved).isTrue()
    }

    @Then("the review by {string} for {string} is not approved")
    fun theReviewIsNotApproved(
        authorLogin: String,
        posName: String
    ) {
        assertThat(currentReview(authorLogin, posName).approved).isFalse()
    }

    @Then("the approval is rejected")
    fun theApprovalIsRejected() {
        assertThat(lastApprovalStatusCode).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    // helpers ---------------------------------------------------------------------

    private fun createReview(
        login: String,
        posName: String,
        text: String
    ) {
        // the author is the authenticated caller, so the body carries no authorId; the review is created
        // as the named login
        val review = ReviewDto(posId = posByName.getValue(posName).id, review = text)
        reviewsByAuthorAndPos[reviewKey(login, posName)] =
            reviewRequests.create(listOf(review), credentialsByLogin.getValue(login)).first()
    }

    private fun currentReview(
        authorLogin: String,
        posName: String
    ): ReviewDto = reviewRequests.retrieveById(reviewsByAuthorAndPos.getValue(reviewKey(authorLogin, posName)).id!!)

    private fun reviewKey(
        login: String,
        posName: String
    ): String = "$login @ $posName"
}
