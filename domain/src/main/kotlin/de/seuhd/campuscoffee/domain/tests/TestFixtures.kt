package de.seuhd.campuscoffee.domain.tests

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import org.apache.commons.lang3.SerializationUtils
import java.time.LocalDateTime

/**
 * Test fixtures for domain objects.
 */
object TestFixtures {
    const val MIN_APPROVAL_COUNT = 3

    private val DATE_TIME: LocalDateTime = LocalDateTime.of(2025, 10, 29, 12, 0, 0)

    private val USER_LIST = listOf(
        User(
            id = 1L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            loginName = "jane_doe", emailAddress = "jane.doe@uni-heidelberg.de",
            firstName = "Jane", lastName = "Doe",
        ),
        User(
            id = 2L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            loginName = "maxmustermann", emailAddress = "max.mustermann@campus.de",
            firstName = "Max", lastName = "Mustermann",
        ),
        User(
            id = 3L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            loginName = "student2023", emailAddress = "student2023@study.org",
            firstName = "Student", lastName = "Example",
        ),
    )

    private val POS_LIST = listOf(
        Pos(
            id = 1L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            name = "Schmelzpunkt", description = "Great waffles",
            type = PosType.CAFE, campus = CampusType.ALTSTADT,
            street = "Hauptstraße", houseNumber = "90", postalCode = 69117, city = "Heidelberg",
        ),
        Pos(
            id = 2L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            name = "Bäcker Görtz ", description = "Walking distance to lecture hall",
            type = PosType.BAKERY, campus = CampusType.INF,
            street = "Berliner Str.", houseNumber = "43", postalCode = 69120, city = "Heidelberg",
        ),
        Pos(
            id = 3L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            name = "Café Botanik", description = "Outdoor seating available",
            type = PosType.CAFETERIA, campus = CampusType.INF,
            street = "Im Neuenheimer Feld", houseNumber = "304", postalCode = 69120, city = "Heidelberg",
        ),
        Pos(
            id = 4L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            name = "New Vending Machine", description = "Use only in case of emergencies",
            type = PosType.VENDING_MACHINE, campus = CampusType.BERGHEIM,
            street = "Teststraße", houseNumber = "99a", postalCode = 12345, city = "Other City",
        ),
    )

    private val REVIEW_LIST = listOf(
        Review(
            id = 1L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            pos = POS_LIST[0], author = USER_LIST[0],
            review = "Great place!", approved = false, approvalCount = 2,
        ),
        Review(
            id = 2L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            pos = POS_LIST[0], author = USER_LIST[1],
            review = "Very nice place!", approved = true, approvalCount = 3,
        ),
        Review(
            id = 3L, createdAt = DATE_TIME, updatedAt = DATE_TIME,
            pos = POS_LIST.last(), author = USER_LIST.last(),
            review = "This place is really bad!", approved = false, approvalCount = 0,
        ),
    )

    @JvmStatic
    fun getUserFixtures(): List<User> = USER_LIST.map { SerializationUtils.clone(it) }

    @JvmStatic
    fun getUserFixturesForInsertion(): List<User> =
        getUserFixtures().map { it.copy(id = null, createdAt = null, updatedAt = null) }

    @JvmStatic
    fun getPosFixtures(): List<Pos> = POS_LIST.map { SerializationUtils.clone(it) }

    @JvmStatic
    fun getPosFixturesForInsertion(): List<Pos> =
        getPosFixtures().map { it.copy(id = null, createdAt = null, updatedAt = null) }

    @JvmStatic
    fun getReviewFixtures(): List<Review> = REVIEW_LIST.map { SerializationUtils.clone(it) }

    @JvmStatic
    fun getReviewFixturesForInsertion(): List<Review> =
        getReviewFixtures().map { it.copy(id = null, createdAt = null, updatedAt = null) }

    @JvmStatic
    fun createUserFixtures(userService: UserService): List<User> =
        getUserFixturesForInsertion().map { userService.upsert(it) }

    @JvmStatic
    fun createPosFixtures(posService: PosService): List<Pos> =
        getPosFixturesForInsertion().map { posService.upsert(it) }

    @JvmStatic
    fun createReviewFixtures(reviewService: ReviewService): List<Review> =
        getReviewFixturesForInsertion().map { reviewService.upsert(it) }

    @JvmStatic
    fun getApprovalConfiguration(): ApprovalConfiguration = ApprovalConfiguration(MIN_APPROVAL_COUNT)
}
