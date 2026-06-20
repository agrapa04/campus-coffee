package de.seuhd.campuscoffee.domain.tests

import de.seuhd.campuscoffee.domain.configuration.ApprovalProperties
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.model.objects.Role
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import java.time.LocalDateTime
import java.util.UUID

/**
 * Test fixtures for domain objects.
 */
@Suppress("TooManyFunctions")
object TestFixtures {
    const val MIN_APPROVAL_COUNT = 3

    private val DATE_TIME: LocalDateTime = LocalDateTime.of(2025, 10, 29, 12, 0, 0)

    // A deterministic UUID for a fixture. UUID(long mostSigBits, long leastSigBits) is the JDK
    // constructor (the two args are the high and low 64 bits), so fixtureId(1) is
    // 00000000-0000-0000-0000-000000000001. The *ForInsertion() helpers strip these before seeding, so
    // they only matter to the unit tests that read them back (e.g. to tell one fixture user from another).
    private fun fixtureId(value: Long): UUID = UUID(0L, value)

    private val USER_LIST =
        listOf(
            User(
                id = fixtureId(1),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                loginName = "jane_doe",
                emailAddress = "jane.doe@uni-heidelberg.de",
                firstName = "Jane",
                lastName = "Doe",
                // the admin fixture is also granted MODERATOR and USER, so it can moderate too
                roles = setOf(Role.USER, Role.MODERATOR, Role.ADMIN),
                password = "aaaMbnPdFYDqkOpS3fVA"
            ),
            User(
                id = fixtureId(2),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                loginName = "maxmustermann",
                emailAddress = "max.mustermann@campus.de",
                firstName = "Max",
                lastName = "Mustermann",
                roles = setOf(Role.USER, Role.MODERATOR),
                password = "AmLtoD3r8lVdnwoLN1Nn"
            ),
            User(
                id = fixtureId(3),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                loginName = "student2023",
                emailAddress = "student2023@study.org",
                firstName = "Student",
                lastName = "Example",
                roles = setOf(Role.USER),
                password = "ZwTwB8Hn8VkNLZec7bR1"
            ),
            User(
                id = fixtureId(4),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                loginName = "lisa_lee",
                emailAddress = "lisa.lee@uni-heidelberg.de",
                firstName = "Lisa",
                lastName = "Lee",
                roles = setOf(Role.USER),
                password = "lG6v9dGKZA5kfOHTFLNR"
            ),
            User(
                id = fixtureId(5),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                loginName = "olivia_admin",
                emailAddress = "olivia.admin@uni-heidelberg.de",
                firstName = "Olivia",
                lastName = "Admin",
                // an admin who is not a moderator: administers users but cannot moderate content
                roles = setOf(Role.USER, Role.ADMIN),
                password = "Qp7r2sV9xKmN4bLdTtYw"
            )
        )

    private val POS_LIST =
        listOf(
            Pos(
                id = fixtureId(1),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                name = "Schmelzpunkt",
                description = "Great waffles",
                type = PosType.CAFE,
                campus = CampusType.ALTSTADT,
                street = "Hauptstraße",
                houseNumber = "90",
                postalCode = "69117",
                city = "Heidelberg"
            ),
            Pos(
                id = fixtureId(2),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                name = "Bäcker Görtz",
                description = "Walking distance to lecture hall",
                type = PosType.BAKERY,
                campus = CampusType.INF,
                street = "Berliner Str.",
                houseNumber = "43",
                postalCode = "69120",
                city = "Heidelberg"
            ),
            Pos(
                id = fixtureId(3),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                name = "Café Botanik",
                description = "Outdoor seating available",
                type = PosType.CAFETERIA,
                campus = CampusType.INF,
                street = "Im Neuenheimer Feld",
                houseNumber = "304",
                postalCode = "69120",
                city = "Heidelberg"
            ),
            Pos(
                id = fixtureId(4),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                name = "New Vending Machine",
                description = "Use only in case of emergencies",
                type = PosType.VENDING_MACHINE,
                campus = CampusType.BERGHEIM,
                street = "Teststraße",
                houseNumber = "99a",
                postalCode = "12345",
                city = "Other City"
            )
        )

    // Each review's approvers (APPROVERS_BY_REVIEW_INDEX) are non-authors, and every non-zero count has
    // matching approver rows. Review 1 reaches the quorum of MIN_APPROVAL_COUNT = 3 and
    // is approved; review 3 has no approvals, so the instructor demo can drive it to approval.
    private val REVIEW_LIST =
        listOf(
            Review(
                id = fixtureId(1),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                pos = POS_LIST[0],
                author = USER_LIST[0],
                review = "Great place!",
                approved = true,
                approvalCount = 3
            ),
            Review(
                id = fixtureId(2),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                pos = POS_LIST[0],
                author = USER_LIST[1],
                review = "Very nice place!",
                approved = false,
                approvalCount = 2
            ),
            Review(
                id = fixtureId(3),
                createdAt = DATE_TIME,
                updatedAt = DATE_TIME,
                pos = POS_LIST.last(),
                author = USER_LIST[2],
                review = "This place is really bad!",
                approved = false,
                approvalCount = 0
            )
        )

    // For each review (by list index), the users (by list index) who approved it. Each approver
    // differs from the review's author and the list length matches the review's approvalCount, so the
    // review_approvals rows are consistent with the counts.
    private val APPROVERS_BY_REVIEW_INDEX =
        mapOf(
            // review 1 (author jane_doe): approved by maxmustermann, student2023, and lisa_lee, reaching
            // the quorum, so it is approved
            0 to listOf(1, 2, 3),
            // review 2 (author maxmustermann): approved by jane_doe and student2023 (below the quorum)
            1 to listOf(0, 2),
            // review 3 (author student2023): no approvals yet (the instructor demo drives this one)
            2 to emptyList()
        )

    fun getUserFixtures(): List<User> = USER_LIST

    /** A fixture user holding exactly [Role.USER] (no moderator or admin grant). */
    fun plainUser(): User = getUserFixtures().first { it.roles == setOf(Role.USER) }

    /** A fixture user granted [Role.MODERATOR] but not [Role.ADMIN] (a content moderator). */
    fun moderator(): User = getUserFixtures().first { Role.MODERATOR in it.roles && Role.ADMIN !in it.roles }

    /** A fixture user granted [Role.ADMIN] (user administration). */
    fun admin(): User = getUserFixtures().first { Role.ADMIN in it.roles }

    /** A fixture user granted [Role.ADMIN] but not [Role.MODERATOR] (administers users, cannot moderate). */
    fun adminWithoutModeration(): User =
        getUserFixtures().first { Role.ADMIN in it.roles && Role.MODERATOR !in it.roles }

    /**
     * The login name and raw password of a fixture user with [role]: [Role.USER] a plain user,
     * [Role.MODERATOR] a moderator, and [Role.ADMIN] the fixture that holds every role. This is the single
     * source of the fixture credentials, reused by the dev endpoint, the startup loader, and the system
     * tests, so a password is defined in exactly one place.
     */
    fun rawCredentialsFor(role: Role): Pair<String, String> =
        when (role) {
            Role.USER -> plainUser()
            Role.MODERATOR -> moderator()
            Role.ADMIN -> getUserFixtures().first { it.roles.containsAll(Role.entries) }
        }.let { requireNotNull(it.loginName) to requireNotNull(it.password) }

    fun getUserFixturesForInsertion(): List<User> =
        getUserFixtures().map { it.copy(id = null, createdAt = null, updatedAt = null) }

    fun getPosFixtures(): List<Pos> = POS_LIST

    /** Any fixture POS, for tests that need one without caring which. */
    fun anyPos(): Pos = getPosFixtures().first()

    fun getPosFixturesForInsertion(): List<Pos> =
        getPosFixtures().map { it.copy(id = null, createdAt = null, updatedAt = null) }

    fun getReviewFixtures(): List<Review> = REVIEW_LIST

    /** A fixture review that has reached the approval quorum. */
    fun approvedReview(): Review = getReviewFixtures().first { it.approved }

    /** A fixture review with no approvals yet. */
    fun unapprovedReview(): Review = getReviewFixtures().first { !it.approved && it.approvalCount == 0 }

    fun getReviewFixturesForInsertion(): List<Review> =
        getReviewFixtures().map { it.copy(id = null, createdAt = null, updatedAt = null) }

    fun createUserFixtures(userService: UserService): List<User> =
        getUserFixturesForInsertion().map { userService.upsert(it) }

    fun createPosFixtures(posService: PosService): List<Pos> =
        getPosFixturesForInsertion().map { posService.upsert(it) }

    /**
     * Persists the fixture reviews, re-pointing each one at the already-persisted POS and author (matched
     * by their natural keys: the POS name and the user login name). A review references its POS and author
     * by id, and those ids are assigned at insertion time rather than known up front, so the fixture POS
     * and author objects cannot be used directly.
     */
    fun createReviewFixtures(
        reviewService: ReviewService,
        createdUsers: List<User>,
        createdPos: List<Pos>
    ): List<Review> {
        val posByName = createdPos.associateBy { it.name }
        val userByLogin = createdUsers.associateBy { it.loginName }
        return getReviewFixturesForInsertion().map { review ->
            reviewService.upsert(
                review.copy(
                    pos = posByName.getValue(review.pos.name),
                    author = userByLogin.getValue(review.author.loginName)
                )
            )
        }
    }

    /**
     * Records the `review_approvals` rows for the fixture reviews: for each review it records one approval
     * per (non-author) approver listed in [APPROVERS_BY_REVIEW_INDEX], using the ids of the
     * already-created users and reviews. This keeps every non-zero `approval_count` backed by matching
     * approver rows. The approve *workflow* that records these at runtime is the subject of the
     * assignment (Exercise 5); the fixtures write them directly.
     *
     * @param createdUsers   the persisted users, in fixture order (their ids are read here)
     * @param createdReviews the persisted reviews, in fixture order (their ids are read here)
     * @return the recorded approvals
     */
    fun createReviewApprovalFixtures(
        reviewApprovalDataService: ReviewApprovalDataService,
        createdUsers: List<User>,
        createdReviews: List<Review>
    ): List<ReviewApproval> =
        APPROVERS_BY_REVIEW_INDEX.entries.flatMap { (reviewIndex, approverIndices) ->
            val reviewId = requireNotNull(createdReviews[reviewIndex].id)
            approverIndices.map { approverIndex ->
                val userId = requireNotNull(createdUsers[approverIndex].id)
                reviewApprovalDataService.record(ReviewApproval(reviewId = reviewId, userId = userId))
            }
        }

    /**
     * Loads the users, POS, reviews, and their approver rows into the given services and returns the
     * counts (users, POS, reviews). Used by the dev endpoint and the optional startup loader.
     */
    fun loadAll(
        userService: UserService,
        posService: PosService,
        reviewService: ReviewService,
        reviewApprovalDataService: ReviewApprovalDataService
    ): Triple<Int, Int, Int> {
        val users = createUserFixtures(userService)
        val pos = createPosFixtures(posService)
        val reviews = createReviewFixtures(reviewService, users, pos)
        createReviewApprovalFixtures(reviewApprovalDataService, users, reviews)
        return Triple(users.size, pos.size, reviews.size)
    }

    fun getApprovalProperties(): ApprovalProperties = ApprovalProperties(MIN_APPROVAL_COUNT)
}
