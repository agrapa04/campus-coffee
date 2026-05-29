package de.seuhd.campuscoffee.api.mapper

import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests [ReviewDtoMapper]. `toDomain` must resolve the POS and author by id and build a review that is
 * unapproved with a zero approval count. `fromDomain` must copy the POS and author ids into the DTO.
 */
class ReviewDtoMapperTest {
    private val mapper: ReviewDtoMapper = Mappers.getMapper(ReviewDtoMapper::class.java)
    private val posService: PosService = mock()
    private val userService: UserService = mock()

    @BeforeEach
    fun injectServices() {
        mapper.posService = posService
        mapper.userService = userService
    }

    @Test
    fun toDomainForcesUnapprovedZeroCountRegardlessOfInput() {
        val pos = TestFixtures.getPosFixtures().first()
        val author = TestFixtures.getUserFixtures().first()
        whenever(posService.getById(pos.id!!)).thenReturn(pos)
        whenever(userService.getById(author.id!!)).thenReturn(author)
        val dto =
            ReviewDto(
                posId = pos.id,
                authorId = author.id,
                review = "A long enough review text.",
                approved = true // the DTO's approved value must be ignored on toDomain
            )

        val result = mapper.toDomain(dto)

        assertThat(result.approved).isFalse()
        assertThat(result.approvalCount).isZero()
        assertThat(result.pos).isEqualTo(pos)
        assertThat(result.author).isEqualTo(author)
        assertThat(result.review).isEqualTo("A long enough review text.")
    }

    @Test
    fun fromDomainProjectsPosAndAuthorIds() {
        val review = TestFixtures.getReviewFixtures().first()

        val dto = mapper.fromDomain(review)

        assertThat(dto.posId).isEqualTo(review.pos.id)
        assertThat(dto.authorId).isEqualTo(review.author.id)
        assertThat(dto.approved).isEqualTo(review.approved)
        assertThat(dto.review).isEqualTo(review.review)
    }
}
