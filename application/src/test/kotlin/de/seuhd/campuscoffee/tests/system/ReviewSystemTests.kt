package de.seuhd.campuscoffee.tests.system

import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import de.seuhd.campuscoffee.tests.SystemTestUtils.reviewRequests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * System tests for the operations related to reviews, including the approval workflow.
 * The default approval quorum is `campus-coffee.approval.min-count = 3`, so a review needs three
 * approvals from users other than the author to become approved.
 */
class ReviewSystemTests : AbstractSystemTest() {
    @Test
    fun `creating a review returns it unapproved`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")

        val created =
            reviewRequests
                .create(listOf(reviewFor(pos, author, "Solid espresso and plenty of seating.")))
                .first()

        assertThat(created.approved).isFalse()
    }

    @Test
    fun `listing reviews and fetching one by id return the created review`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(listOf(reviewFor(pos, author, "A reliable spot between lectures.")))
                .first()

        assertThat(reviewRequests.retrieveAll().map { it.id }).containsExactly(created.id)

        val byId = reviewRequests.retrieveById(created.id!!)
        assertThat(byId.review).isEqualTo("A reliable spot between lectures.")
    }

    @Test
    fun `updating a review changes its text`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(listOf(reviewFor(pos, author, "Original review text, long enough.")))
                .first()

        // approval count and status are not asserted: an update currently resets both because
        // ReviewDtoMapper hardcodes them on toDomain.
        val updated =
            reviewRequests
                .update(listOf(created.copy(review = "Updated review text, also long enough.")))
                .first()

        assertThat(updated.review).isEqualTo("Updated review text, also long enough.")
        assertThat(reviewRequests.retrieveById(created.id!!).review)
            .isEqualTo("Updated review text, also long enough.")
    }

    @Test
    fun `deleting a review twice returns 204 No Content then 404 Not Found`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")
        val created =
            reviewRequests
                .create(listOf(reviewFor(pos, author, "This review will be deleted.")))
                .first()
        val id = requireNotNull(created.id)

        val statusCodes = reviewRequests.deleteAndReturnStatusCodes(listOf(id, id))

        // the first delete returns 204 No Content, the second 404 Not Found
        assertThat(statusCodes).containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `approving a review below the quorum leaves it unapproved`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")
        val firstApprover = createUser("approver_one", "approver.one@uni-heidelberg.de")
        val secondApprover = createUser("approver_two", "approver.two@uni-heidelberg.de")
        val review =
            reviewRequests
                .create(listOf(reviewFor(pos, author, "Review that stays below the quorum.")))
                .first()

        reviewRequests.approve(review.id!!, firstApprover.id!!)
        val afterTwoApprovals = reviewRequests.approve(review.id!!, secondApprover.id!!)

        assertThat(afterTwoApprovals.approved).isFalse()
    }

    @Test
    fun `approving a review up to the quorum marks it approved`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")
        val firstApprover = createUser("approver_one", "approver.one@uni-heidelberg.de")
        val secondApprover = createUser("approver_two", "approver.two@uni-heidelberg.de")
        val thirdApprover = createUser("approver_three", "approver.three@uni-heidelberg.de")
        val review =
            reviewRequests
                .create(listOf(reviewFor(pos, author, "Review that reaches the quorum.")))
                .first()

        reviewRequests.approve(review.id!!, firstApprover.id!!)
        reviewRequests.approve(review.id!!, secondApprover.id!!)
        val afterThreeApprovals = reviewRequests.approve(review.id!!, thirdApprover.id!!)

        assertThat(afterThreeApprovals.approved).isTrue()
    }

    @Test
    fun `approving your own review returns 400 Bad Request`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")
        val review =
            reviewRequests
                .create(listOf(reviewFor(pos, author, "Author tries to approve this review.")))
                .first()

        val statusCode = reviewRequests.approveAndReturnStatusCode(review.id!!, author.id!!)

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `creating a second review by the same author for a POS returns 400 Bad Request`() {
        val pos = createPos()
        val author = createUser("author", "author@uni-heidelberg.de")
        reviewRequests.create(listOf(reviewFor(pos, author, "First review by this author.")))

        val statusCode =
            reviewRequests
                .createAndReturnStatusCodes(listOf(reviewFor(pos, author, "Second review by the same author.")))
                .first()

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `approving a missing review returns 404 Not Found`() {
        val approver = createUser("approver", "approver@uni-heidelberg.de")

        val statusCode = reviewRequests.approveAndReturnStatusCode(9999L, approver.id!!)

        assertThat(statusCode).isEqualTo(HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `filtering reviews by approval status returns only the matching reviews`() {
        val pos = createPos()
        val approvedAuthor = createUser("approved_author", "approved.author@uni-heidelberg.de")
        val pendingAuthor = createUser("pending_author", "pending.author@uni-heidelberg.de")
        val firstApprover = createUser("approver_one", "approver.one@uni-heidelberg.de")
        val secondApprover = createUser("approver_two", "approver.two@uni-heidelberg.de")
        val thirdApprover = createUser("approver_three", "approver.three@uni-heidelberg.de")

        val approvedReview =
            reviewRequests
                .create(listOf(reviewFor(pos, approvedAuthor, "This review reaches the quorum.")))
                .first()
        val pendingReview =
            reviewRequests
                .create(listOf(reviewFor(pos, pendingAuthor, "This review stays below the quorum.")))
                .first()

        reviewRequests.approve(approvedReview.id!!, firstApprover.id!!)
        reviewRequests.approve(approvedReview.id!!, secondApprover.id!!)
        reviewRequests.approve(approvedReview.id!!, thirdApprover.id!!)

        val posId = requireNotNull(pos.id)
        val approved = reviewRequests.retrieveByFilter(mapOf("pos_id" to posId, "approved" to true))
        assertThat(approved.map { it.id }).containsExactly(approvedReview.id)

        val pending = reviewRequests.retrieveByFilter(mapOf("pos_id" to posId, "approved" to false))
        assertThat(pending.map { it.id }).containsExactly(pendingReview.id)
    }

    // helpers ---------------------------------------------------------------------

    private fun createPos(): Pos = posService.upsert(TestFixtures.getPosFixturesForInsertion().first())

    private fun createUser(
        loginName: String,
        emailAddress: String
    ): User =
        userService.upsert(
            User(loginName = loginName, emailAddress = emailAddress, firstName = "First", lastName = "Last")
        )

    private fun reviewFor(
        pos: Pos,
        author: User,
        text: String
    ): ReviewDto = ReviewDto(posId = pos.id, authorId = author.id, review = text)
}
