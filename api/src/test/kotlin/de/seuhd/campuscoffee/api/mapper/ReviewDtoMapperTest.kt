package de.seuhd.campuscoffee.api.mapper

import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests [ReviewDtoMapper]. `toDomain` must resolve the POS by id, take the author from the authenticated
 * user (not the DTO), and build a review that is unapproved with a zero approval count. `fromDomain` must
 * copy the POS and author ids into the DTO.
 */
class ReviewDtoMapperTest {
    private val mapper: ReviewDtoMapper = Mappers.getMapper(ReviewDtoMapper::class.java)
    private val posService: PosService = mock()

    @BeforeEach
    fun injectServices() {
        mapper.posService = posService
    }

    @Test
    fun `toDomain forces a new review to be unapproved and takes the author from the acting user`() {
        val pos = TestFixtures.getPosFixtures().first()
        val author = TestFixtures.getUserFixtures().first()
        whenever(posService.getById(pos.id!!)).thenReturn(pos)
        val dto =
            ReviewDto(
                posId = pos.id,
                review = "A long enough review text.",
                approved = true // the DTO's approved value must be ignored on toDomain
            )

        val result = mapper.toDomain(dto, author)

        assertThat(result.approved).isFalse()
        assertThat(result.approvalCount).isZero()
        assertThat(result.pos).isEqualTo(pos)
        assertThat(result.author).isEqualTo(author)
        assertThat(result.review).isEqualTo("A long enough review text.")
    }

    @Test
    fun `fromDomain copies the POS and author ids into the DTO`() {
        val review = TestFixtures.getReviewFixtures().first()

        val dto = mapper.fromDomain(review)

        assertThat(dto.posId).isEqualTo(review.pos.id)
        assertThat(dto.authorId).isEqualTo(review.author.id)
        assertThat(dto.approved).isEqualTo(review.approved)
        assertThat(dto.review).isEqualTo(review.review)
    }

    @Test
    fun `the single-argument toDomain is unsupported because the author is the authenticated user`() {
        // the DtoMapper contract's single-arg toDomain cannot build a review (the author is the
        // authenticated user, not a DTO field), so it throws; the controller uses the two-arg overload
        assertThrows<UnsupportedOperationException> { mapper.toDomain(ReviewDto(posId = 1L, review = "x")) }
    }
}
