package de.seuhd.campuscoffee.data.integration

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.data.persistence.entities.UserEntity
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Integration tests for the custom queries on
 * [de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository].
 */
class ReviewRepositoryIntegrationTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posEntityMapper: PosEntityMapper

    @Autowired
    private lateinit var userEntityMapper: UserEntityMapper

    @Test
    fun `findAllByPosAndApproved returns reviews matching the approval status`() {
        val pos = persistFirstPos()
        val users = TestFixtures.getUserFixturesForInsertion()
        val approved = persistReview(pos, persistUser(users[0]), true)
        val pending = persistReview(pos, persistUser(users[1]), false)

        assertThat(reviewRepository.findAllByPosAndApproved(pos, true).map { it.id })
            .containsExactly(approved.id)
        assertThat(reviewRepository.findAllByPosAndApproved(pos, false).map { it.id })
            .containsExactly(pending.id)
    }

    @Test
    fun `findAllByPosAndAuthor returns only reviews by that author`() {
        val pos = persistFirstPos()
        val users = TestFixtures.getUserFixturesForInsertion()
        val author = persistUser(users[0])
        val otherAuthor = persistUser(users[1])
        val review = persistReview(pos, author, false)

        assertThat(reviewRepository.findAllByPosAndAuthor(pos, author).map { it.id })
            .containsExactly(review.id)
        assertThat(reviewRepository.findAllByPosAndAuthor(pos, otherAuthor)).isEmpty()
    }

    private fun persistFirstPos(): PosEntity =
        posRepository.save(posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion().first()))

    private fun persistUser(user: User): UserEntity = userRepository.save(userEntityMapper.toEntity(user))

    private fun persistReview(
        pos: PosEntity,
        author: UserEntity,
        approved: Boolean
    ): ReviewEntity {
        val entity =
            ReviewEntity().apply {
                this.pos = pos
                this.author = author
                review = "A review with enough characters."
                approvalCount = if (approved) 3 else 0
                this.approved = approved
            }
        return reviewRepository.save(entity)
    }
}
