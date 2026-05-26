package de.seuhd.campuscoffee.api.mapper;

import de.seuhd.campuscoffee.api.dtos.ReviewDto;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.api.PosService;
import de.seuhd.campuscoffee.domain.ports.api.UserService;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ReviewDtoMapper}. {@code toDomain} must resolve the POS and author by id and build a
 * review that is unapproved with a zero approval count. {@code fromDomain} must copy the POS and author
 * ids into the DTO.
 */
class ReviewDtoMapperTest {

    private final ReviewDtoMapper mapper = Mappers.getMapper(ReviewDtoMapper.class);
    private final PosService posService = mock(PosService.class);
    private final UserService userService = mock(UserService.class);

    @BeforeEach
    void injectServices() {
        mapper.posService = posService;
        mapper.userService = userService;
    }

    @Test
    void toDomainForcesUnapprovedZeroCountRegardlessOfInput() {
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        User author = TestFixtures.getUserFixtures().getFirst();
        when(posService.getById(pos.getId())).thenReturn(pos);
        when(userService.getById(author.getId())).thenReturn(author);
        ReviewDto dto = ReviewDto.builder()
                .posId(pos.getId())
                .authorId(author.getId())
                .review("A long enough review text.")
                .approved(true) // the DTO's approved value must be ignored on toDomain
                .build();

        Review result = mapper.toDomain(dto);

        assertThat(result.approved()).isFalse();
        assertThat(result.approvalCount()).isZero();
        assertThat(result.pos()).isEqualTo(pos);
        assertThat(result.author()).isEqualTo(author);
        assertThat(result.review()).isEqualTo("A long enough review text.");
    }

    @Test
    void fromDomainProjectsPosAndAuthorIds() {
        Review review = TestFixtures.getReviewFixtures().getFirst();

        ReviewDto dto = mapper.fromDomain(review);

        assertThat(dto.posId()).isEqualTo(review.pos().getId());
        assertThat(dto.authorId()).isEqualTo(review.author().getId());
        assertThat(dto.approved()).isEqualTo(review.approved());
        assertThat(dto.review()).isEqualTo(review.review());
    }
}
