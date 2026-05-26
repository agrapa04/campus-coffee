package de.seuhd.campuscoffee.data.integration;

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper;
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper;
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity;
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity;
import de.seuhd.campuscoffee.data.persistence.entities.UserEntity;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the custom queries on
 * {@link de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository}.
 */
class ReviewRepositoryIntegrationTest extends AbstractDataIntegrationTest {

    @Autowired
    private PosEntityMapper posEntityMapper;

    @Autowired
    private UserEntityMapper userEntityMapper;

    @Test
    void findAllByPosAndApprovedPartitionsByStatus() {
        PosEntity pos = persistFirstPos();
        List<User> users = TestFixtures.getUserFixturesForInsertion();
        ReviewEntity approved = persistReview(pos, persistUser(users.get(0)), true);
        ReviewEntity pending = persistReview(pos, persistUser(users.get(1)), false);

        assertThat(reviewRepository.findAllByPosAndApproved(pos, true))
                .extracting(ReviewEntity::getId).containsExactly(approved.getId());
        assertThat(reviewRepository.findAllByPosAndApproved(pos, false))
                .extracting(ReviewEntity::getId).containsExactly(pending.getId());
    }

    @Test
    void findAllByPosAndAuthorReturnsOnlyThatAuthorsReviews() {
        PosEntity pos = persistFirstPos();
        List<User> users = TestFixtures.getUserFixturesForInsertion();
        UserEntity author = persistUser(users.get(0));
        UserEntity otherAuthor = persistUser(users.get(1));
        ReviewEntity review = persistReview(pos, author, false);

        assertThat(reviewRepository.findAllByPosAndAuthor(pos, author))
                .extracting(ReviewEntity::getId).containsExactly(review.getId());
        assertThat(reviewRepository.findAllByPosAndAuthor(pos, otherAuthor)).isEmpty();
    }

    private PosEntity persistFirstPos() {
        return posRepository.save(posEntityMapper.toEntity(TestFixtures.getPosFixturesForInsertion().getFirst()));
    }

    private UserEntity persistUser(User user) {
        return userRepository.save(userEntityMapper.toEntity(user));
    }

    private ReviewEntity persistReview(PosEntity pos, UserEntity author, boolean approved) {
        ReviewEntity review = new ReviewEntity();
        review.setPos(pos);
        review.setAuthor(author);
        review.setReview("A review with enough characters.");
        review.setApprovalCount(approved ? 3 : 0);
        review.setApproved(approved);
        return reviewRepository.save(review);
    }
}
