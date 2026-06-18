package de.seuhd.campuscoffee.tests.acceptance

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.api.mapper.PosDtoMapper
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.ADMIN
import de.seuhd.campuscoffee.tests.SystemTestUtils.ADMIN_NO_MOD
import de.seuhd.campuscoffee.tests.SystemTestUtils.Credentials
import de.seuhd.campuscoffee.tests.SystemTestUtils.MODERATOR
import de.seuhd.campuscoffee.tests.SystemTestUtils.USER
import de.seuhd.campuscoffee.tests.SystemTestUtils.posRequests
import de.seuhd.campuscoffee.tests.SystemTestUtils.reviewRequests
import io.cucumber.java.ParameterType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpStatus

/**
 * Step definitions for the role-based authorization scenarios. Each step names a role and resolves it to
 * the seeded fixture user's credentials.
 */
class CucumberAuthSteps(
    private val posDtoMapper: PosDtoMapper
) {
    private var lastStatus = 0
    private var lastReview: ReviewDto? = null

    private fun credentialsFor(role: String): Credentials =
        when (role) {
            "a plain user" -> USER
            "a moderator" -> MODERATOR
            "an admin" -> ADMIN
            "an admin without moderation" -> ADMIN_NO_MOD
            else -> error("unknown role '$role'")
        }

    // lets the scenarios name a role as a bare phrase instead of a quoted {string}
    @ParameterType("a plain user|a moderator|an admin without moderation|an admin")
    fun role(name: String): String = name

    private fun aPos(): PosDto = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().first())

    @When("an unauthenticated user creates a point of sale")
    fun unauthenticatedCreatesPos() {
        lastStatus = posRequests.createUnauthenticatedAndReturnStatusCode(aPos())
    }

    @When("{role} creates a point of sale")
    fun roleCreatesPos(role: String) {
        lastStatus = posRequests.createAndReturnStatusCodes(listOf(aPos()), credentialsFor(role)).first()
    }

    @Given("a review authored by {role}")
    fun aReviewAuthoredBy(role: String) {
        // POS creation needs MODERATOR; the named role only authors the review
        val pos = posRequests.create(listOf(aPos()), MODERATOR).first()
        val review = ReviewDto(posId = pos.id, review = "A review for the authorization checks.")
        lastReview = reviewRequests.create(listOf(review), credentialsFor(role)).first()
    }

    @When("{role} edits that review")
    fun roleEditsReview(role: String) {
        val edit = lastReview!!.copy(review = "Edited for the authorization checks, long enough.")
        lastStatus = reviewRequests.updateAndReturnStatusCodes(listOf(edit), credentialsFor(role)).first()
    }

    @When("{role} approves that review")
    fun roleApprovesReview(role: String) {
        lastStatus = reviewRequests.approveAndReturnStatusCode(lastReview!!.id!!, credentialsFor(role))
    }

    @When("{role} approves that review again")
    fun roleApprovesReviewAgain(role: String) {
        lastStatus = reviewRequests.approveAndReturnStatusCode(lastReview!!.id!!, credentialsFor(role))
    }

    @Then("the write request is unauthorized")
    fun writeUnauthorized() {
        assertThat(lastStatus).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Then("the write request is forbidden")
    fun writeForbidden() {
        assertThat(lastStatus).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Then("the write request succeeds")
    fun writeSucceeds() {
        assertThat(lastStatus).isIn(HttpStatus.OK.value(), HttpStatus.CREATED.value())
    }

    @Then("the write request is a conflict")
    fun writeConflict() {
        assertThat(lastStatus).isEqualTo(HttpStatus.CONFLICT.value())
    }

    @Then("the write request is a bad request")
    fun writeBadRequest() {
        assertThat(lastStatus).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }
}
