package de.seuhd.campuscoffee.tests.system;

import de.seuhd.campuscoffee.api.dtos.PosDto;
import de.seuhd.campuscoffee.api.dtos.ReviewDto;
import de.seuhd.campuscoffee.api.dtos.UserDto;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.posRequests;
import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.reviewRequests;
import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.userRequests;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests that pin the HTTP status codes produced by the global exception handler:
 * duplicate unique fields return 409, missing entities return 404, and invalid input returns 400.
 */
public class ErrorPathSystemTests extends AbstractSysTest {

    private static final long MISSING_ID = 9999L;

    @Test
    void duplicatePosNameReturnsConflict() {
        PosDto pos = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().getFirst());
        posRequests.create(List.of(pos));

        int statusCode = posRequests.createAndReturnStatusCodes(List.of(pos)).getFirst();

        assertThat(statusCode).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void duplicateUserReturnsConflict() {
        UserDto user = userDtoMapper.fromDomain(TestFixtures.getUserFixturesForInsertion().getFirst());
        userRequests.create(List.of(user));

        int statusCode = userRequests.createAndReturnStatusCodes(List.of(user)).getFirst();

        assertThat(statusCode).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void getByMissingIdReturnsNotFound() {
        assertThat(posRequests.retrieveByIdStatusCode(MISSING_ID)).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(userRequests.retrieveByIdStatusCode(MISSING_ID)).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(reviewRequests.retrieveByIdStatusCode(MISSING_ID)).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void filterByNonexistentValueReturnsNotFound() {
        assertThat(posRequests.retrieveByFilterStatusCode("name", "NoSuchPosName"))
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(userRequests.retrieveByFilterStatusCode("login_name", "no_such_login"))
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void updateMissingPosReturnsNotFound() {
        PosDto missing = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().getFirst())
                .toBuilder().id(MISSING_ID).build();

        int statusCode = posRequests.updateAndReturnStatusCodes(List.of(missing)).getFirst();

        assertThat(statusCode).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void updateWithMismatchedPathAndBodyIdReturnsBadRequest() {
        PosDto created = posRequests
                .create(List.of(posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().getFirst())))
                .getFirst();

        int statusCode = posRequests.updateWithPathIdAndReturnStatusCode(created.getId() + 1, created);

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void posWithBlankRequiredFieldReturnsBadRequest() {
        PosDto invalid = posDtoMapper.fromDomain(TestFixtures.getPosFixturesForInsertion().getFirst())
                .toBuilder().city("").build();

        int statusCode = posRequests.createAndReturnStatusCodes(List.of(invalid)).getFirst();

        assertThat(statusCode).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void reviewWithInvalidTextLengthReturnsBadRequest() {
        // an empty review is rejected by bean validation; this pins the controller-to-400 mapping for a
        // validation failure without depending on the exact length bounds
        ReviewDto invalid = ReviewDto.builder().posId(1L).authorId(1L).review("").build();

        assertThat(reviewRequests.createAndReturnStatusCodes(List.of(invalid)).getFirst())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void reviewWithNullReferencesReturnsBadRequest() {
        ReviewDto missingPos = ReviewDto.builder().authorId(1L).review("Valid length review text.").build();
        ReviewDto missingAuthor = ReviewDto.builder().posId(1L).review("Valid length review text.").build();

        assertThat(reviewRequests.createAndReturnStatusCodes(List.of(missingPos)).getFirst())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(reviewRequests.createAndReturnStatusCodes(List.of(missingAuthor)).getFirst())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
