package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.constraints.ConstraintMapping
import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.mapper.ReviewEntityMapper
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.IdGenerator
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Data-layer adapter implementing the review data service port. The (pos, author) pair is unique: the
 * database constraint is the authoritative guard for the "one review per author per POS" rule, closing
 * the race the domain-level check-then-act cannot.
 */
@Service(ReviewDataServiceImpl.BEAN_NAME)
class ReviewDataServiceImpl(
    repository: ReviewRepository,
    entityMapper: ReviewEntityMapper,
    private val posEntityMapper: PosEntityMapper,
    private val userEntityMapper: UserEntityMapper,
    idGenerator: IdGenerator
) : CrudDataServiceImpl<Review, ReviewEntity, ReviewRepository, UUID>(
        repository,
        entityMapper,
        Review::class.java,
        setOf(
            ConstraintMapping(
                { "POS ${it.pos.id}, author ${it.author.id}" },
                "pos_id/author_id",
                ReviewEntity.POS_AUTHOR_UNIQUE_CONSTRAINT
            )
        ),
        idGenerator
    ),
    ReviewDataService {
    override fun filter(
        pos: Pos,
        approved: Boolean
    ): List<Review> =
        repository
            .findAllByPosAndApproved(posEntityMapper.toEntity(pos), approved)
            .map { mapper.fromEntity(it) }

    override fun filter(
        pos: Pos,
        author: User
    ): List<Review> =
        repository
            .findAllByPosAndAuthor(posEntityMapper.toEntity(pos), userEntityMapper.toEntity(author))
            .map { mapper.fromEntity(it) }

    companion object {
        /**
         * Spring bean name of this relational adapter. The event-sourcing decorator qualifies on it to wrap
         * this bean. Without the qualifier, Spring would select the `@Primary` decorator as its own
         * [ReviewDataService] delegate.
         */
        const val BEAN_NAME = "reviewDataServiceImpl"
    }
}
