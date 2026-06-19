package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.domain.exceptions.DeletionConflictException
import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

/**
 * Integration tests for the event-sourcing data adapters against a real PostgreSQL container: every write
 * appends the right event and projects the read model, rollbacks leave the log clean, the event bodies
 * never carry a raw password (and review events never carry a password hash), a clear empties both the log
 * and the tables, and replaying the log reconstructs the read model faithfully.
 */
class EventSourcingDataIntegrationTest : AbstractEventSourcingDataIntegrationTest() {
    @Autowired
    private lateinit var projector: ReadModelProjector

    @Test
    fun `creating a POS appends one INSERT event and projects the row`() {
        val created = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())

        val events = eventRepository.findAll()
        assertThat(events).hasSize(1)
        val event = events.first()
        assertThat(event.changeType).isEqualTo(ChangeType.INSERT)
        assertThat(event.entityType).isEqualTo("Pos")
        assertThat(event.entityVersion).isEqualTo(EventStore.PAYLOAD_SCHEMA_VERSION)
        assertThat(event.body?.get("id")).isEqualTo(created.id.toString())
        assertThat(event.body?.get("name")).isEqualTo(created.name)
        // the read model now serves the row
        assertThat(posDataService.getById(requireNotNull(created.id)).name).isEqualTo(created.name)
    }

    @Test
    fun `updating a POS appends an UPDATE event carrying the new state`() {
        val created = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())

        val updated = posDataService.upsert(created.copy(description = "Updated description"))

        val updateEvents = eventRepository.findAll().filter { it.changeType == ChangeType.UPDATE }
        assertThat(updateEvents).hasSize(1)
        assertThat(updateEvents.first().body?.get("description")).isEqualTo("Updated description")
        assertThat(posDataService.getById(requireNotNull(updated.id)).description).isEqualTo("Updated description")
    }

    @Test
    fun `updating a User appends an UPDATE event and projects the change`() {
        val created = userDataService.upsert(TestFixtures.getUserFixturesForInsertion().first())

        val updated = userDataService.upsert(created.copy(firstName = "Renamed"))

        assertThat(eventRepository.findAll().count { it.changeType == ChangeType.UPDATE && it.entityType == "User" })
            .isEqualTo(1)
        assertThat(userDataService.getById(requireNotNull(updated.id)).firstName).isEqualTo("Renamed")
    }

    @Test
    fun `updating a Review appends an UPDATE event and projects the change`() {
        val (review, _, _) = seedReview()

        val updated = reviewDataService.upsert(review.copy(approvalCount = 1, approved = false))

        assertThat(eventRepository.findAll().count { it.changeType == ChangeType.UPDATE && it.entityType == "Review" })
            .isEqualTo(1)
        assertThat(reviewDataService.getById(requireNotNull(updated.id)).approvalCount).isEqualTo(1)
    }

    @Test
    fun `deleting a User appends a DELETE event and removes the row`() {
        val created = userDataService.upsert(TestFixtures.getUserFixturesForInsertion().first())
        val id = requireNotNull(created.id)

        userDataService.delete(id)

        assertThat(eventRepository.findAll().count { it.changeType == ChangeType.DELETE && it.entityType == "User" })
            .isEqualTo(1)
        assertThatThrownBy { userDataService.getById(id) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `deleting a Review appends a DELETE event and removes the row`() {
        val (review, _, _) = seedReview()
        val id = requireNotNull(review.id)

        reviewDataService.delete(id)

        assertThat(eventRepository.findAll().count { it.changeType == ChangeType.DELETE && it.entityType == "Review" })
            .isEqualTo(1)
        assertThatThrownBy { reviewDataService.getById(id) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `deleting a User still referenced by a review throws DeletionConflictException`() {
        val (_, _, author) = seedReview()

        assertThatThrownBy { userDataService.delete(requireNotNull(author.id)) }
            .isInstanceOf(DeletionConflictException::class.java)
    }

    @Test
    fun `deleting a POS appends a DELETE event and removes the row`() {
        val created = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        val id = requireNotNull(created.id)

        posDataService.delete(id)

        val deleteEvents = eventRepository.findAll().filter { it.changeType == ChangeType.DELETE }
        assertThat(deleteEvents).hasSize(1)
        assertThat(deleteEvents.first().body?.get("id")).isEqualTo(id.toString())
        assertThatThrownBy { posDataService.getById(id) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `a duplicate POS name throws DuplicationException and appends no event`() {
        val pos = TestFixtures.getPosFixturesForInsertion().first()
        posDataService.upsert(pos)
        val eventCountAfterFirst = eventRepository.count()

        assertThatThrownBy { posDataService.upsert(pos) }.isInstanceOf(DuplicationException::class.java)

        // the duplicate rolled back, so no second event was kept in the log
        assertThat(eventRepository.count()).isEqualTo(eventCountAfterFirst)
    }

    @Test
    fun `a non-uniqueness violation propagates instead of being mapped to DuplicationException`() {
        // an empty description violates a CHECK constraint, not a uniqueness one, so the projection
        // surfaces the raw integrity violation rather than a DuplicationException
        val invalid = TestFixtures.getPosFixturesForInsertion().first().copy(description = "")

        assertThatThrownBy { posDataService.upsert(invalid) }
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)
            .isNotInstanceOf(DuplicationException::class.java)
        assertThat(eventRepository.count()).isZero()
    }

    @Test
    fun `recording an approval appends an INSERT event and a repeat throws DuplicationException`() {
        val (review, _, _) = seedReview()
        val approver = userDataService.upsert(TestFixtures.getUserFixturesForInsertion()[1])
        val approval = ReviewApproval(reviewId = requireNotNull(review.id), userId = requireNotNull(approver.id))

        reviewApprovalDataService.record(approval)

        assertThat(eventRepository.findAll().count { it.entityType == "ReviewApproval" }).isEqualTo(1)
        assertThat(reviewApprovalDataService.countByReviewId(requireNotNull(review.id))).isEqualTo(1L)
        assertThatThrownBy { reviewApprovalDataService.record(approval) }
            .isInstanceOf(DuplicationException::class.java)
    }

    @Test
    fun `deleting a POS still referenced by a review throws DeletionConflictException`() {
        val (_, pos, _) = seedReview()

        assertThatThrownBy { posDataService.delete(requireNotNull(pos.id)) }
            .isInstanceOf(DeletionConflictException::class.java)
    }

    @Test
    fun `a User event body keeps the password hash but never the raw password`() {
        val user =
            TestFixtures
                .getUserFixturesForInsertion()
                .first()
                .copy(passwordHash = "{bcrypt}\$2a\$10\$abcdefghijklmnopqrstuv", password = "rawPlaintextSecret")

        userDataService.upsert(user)

        val body = requireNotNull(eventRepository.findAll().single { it.entityType == "User" }.body)
        assertThat(body).containsEntry("passwordHash", user.passwordHash)
        assertThat(body).doesNotContainKey("password")
        assertThat(body.values.map { it.toString() }).noneMatch { it.contains("rawPlaintextSecret") }
    }

    @Test
    fun `a Review event body stores POS and author ids and no leaked password hash`() {
        val (_, pos, author) = seedReview()

        val body = requireNotNull(eventRepository.findAll().single { it.entityType == "Review" }.body)
        assertThat(body).containsEntry("posId", pos.id.toString())
        assertThat(body).containsEntry("authorId", author.id.toString())
        assertThat(body.keys).doesNotContain("pos", "author", "passwordHash", "password")
    }

    @Test
    fun `clear empties both the read tables and the event log`() {
        seedReview()
        assertThat(eventRepository.count()).isGreaterThan(0)

        reviewApprovalDataService.clear()
        reviewDataService.clear()
        posDataService.clear()
        userDataService.clear()

        assertThat(eventRepository.count()).isZero()
        assertThat(posDataService.getAll()).isEmpty()
        assertThat(userDataService.getAll()).isEmpty()
        assertThat(reviewDataService.getAll()).isEmpty()
    }

    @Test
    fun `replaying the log reconstructs ids, business fields, and timestamps`() {
        seedReview()
        val posBefore = posDataService.getAll().sortedBy { it.name }
        val usersBefore = userDataService.getAll().sortedBy { it.loginName }
        val reviewsBefore = reviewDataService.getAll().sortedBy { it.review }

        // wipe only the read tables, keeping the log, then replay it through the same projector
        reviewApprovalRepository.deleteAllInBatch()
        reviewRepository.deleteAllInBatch()
        posRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        eventRepository.findAllByOrderBySeqAsc().forEach { projector.apply(it) }

        // ids, business fields, and the createdAt/updatedAt timestamps survive the round-trip unchanged
        assertThat(posDataService.getAll().sortedBy { it.name }).usingRecursiveComparison().isEqualTo(posBefore)
        assertThat(userDataService.getAll().sortedBy { it.loginName }).usingRecursiveComparison().isEqualTo(usersBefore)
        assertThat(
            reviewDataService.getAll().sortedBy { it.review }
        ).usingRecursiveComparison().isEqualTo(reviewsBefore)
    }

    @Test
    fun `applying a review event whose POS is absent throws NotFoundException`() {
        // a review INSERT event referencing a POS that was never projected (a corrupt or out-of-order log);
        // the projector must fail loudly rather than write a dangling row
        val orphanReview = EventBodies.review(posId = UUID.randomUUID(), authorId = UUID.randomUUID())

        assertThatThrownBy { projector.apply(ChangeType.INSERT, "Review", orphanReview) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `applying a review event whose author is absent throws NotFoundException`() {
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        // the POS resolves, but the author id points at no projected user
        val orphanAuthor = EventBodies.review(posId = requireNotNull(pos.id), authorId = UUID.randomUUID())

        assertThatThrownBy { projector.apply(ChangeType.INSERT, "Review", orphanAuthor) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `applying an UPDATE event for a row that is absent throws NotFoundException`() {
        val created = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        val body = requireNotNull(eventRepository.findAll().single { it.entityType == "Pos" }.body)
        // drop the row but keep the body: an UPDATE now has nothing to update
        posRepository.deleteAllInBatch()

        assertThatThrownBy { projector.apply(ChangeType.UPDATE, "Pos", body) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `a duplicate user login name throws DuplicationException via the projector`() {
        val user = TestFixtures.getUserFixturesForInsertion().first()
        userDataService.upsert(user)
        // same login name, different email, so the login-name constraint is the one violated
        assertThatThrownBy { userDataService.upsert(user.copy(emailAddress = "other_${user.emailAddress}")) }
            .isInstanceOf(DuplicationException::class.java)
    }

    @Test
    fun `a duplicate user email throws DuplicationException via the projector`() {
        val user = TestFixtures.getUserFixturesForInsertion().first()
        userDataService.upsert(user)
        // same email, different login name, so the email constraint is the one violated
        assertThatThrownBy { userDataService.upsert(user.copy(loginName = "${user.loginName}_other")) }
            .isInstanceOf(DuplicationException::class.java)
    }

    @Test
    fun `a duplicate review for the same POS and author throws DuplicationException via the projector`() {
        val (_, pos, author) = seedReview()
        // calling the data service directly bypasses the domain pre-check, so the database constraint
        // (mapped by the projector) is the guard that rejects the duplicate
        val duplicate =
            Review(
                pos = pos,
                author = author,
                review = "A second review, long enough.",
                approvalCount = 0,
                approved = false
            )

        assertThatThrownBy { reviewDataService.upsert(duplicate) }.isInstanceOf(DuplicationException::class.java)
    }

    @Test
    fun `replaying a log that includes a review deletion leaves the read model consistent`() {
        val (review, _, _) = seedReview()
        val approver = userDataService.upsert(TestFixtures.getUserFixturesForInsertion()[1])
        reviewApprovalDataService.record(
            ReviewApproval(reviewId = requireNotNull(review.id), userId = requireNotNull(approver.id))
        )
        // deleting the review cascade-removes its approval row but appends only a Review DELETE event
        reviewDataService.delete(requireNotNull(review.id))

        // rebuild the read model from the whole log
        reviewApprovalRepository.deleteAllInBatch()
        reviewRepository.deleteAllInBatch()
        posRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        eventRepository.findAllByOrderBySeqAsc().forEach { projector.apply(it) }

        // the Review DELETE event's cascade removes the approval on replay too, so the result is consistent:
        // the review and its approval are gone, while the POS and both users survive
        assertThat(reviewDataService.getAll()).isEmpty()
        assertThat(reviewApprovalDataService.countByReviewId(requireNotNull(review.id))).isZero()
        assertThat(posDataService.getAll()).hasSize(1)
        assertThat(userDataService.getAll()).hasSize(2)
    }

    /** Seeds a POS, an author, and a review referencing both, and returns them (ids assigned). */
    private fun seedReview(): Triple<Review, Pos, User> {
        val pos = posDataService.upsert(TestFixtures.getPosFixturesForInsertion().first())
        val author =
            userDataService.upsert(
                TestFixtures.getUserFixturesForInsertion().first().copy(
                    passwordHash = "{bcrypt}\$2a\$10\$seededhashvalue000000"
                )
            )
        val review =
            reviewDataService.upsert(
                Review(
                    pos = pos,
                    author = author,
                    review = "A review long enough to pass validation.",
                    approvalCount = 0,
                    approved = false
                )
            )
        return Triple(review, pos, author)
    }
}
