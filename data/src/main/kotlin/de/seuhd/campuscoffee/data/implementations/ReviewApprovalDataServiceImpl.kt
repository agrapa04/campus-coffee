package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.constraints.ConstraintMapping
import de.seuhd.campuscoffee.data.mapper.ReviewApprovalEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.ReviewApprovalEntity
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewApprovalRepository
import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval
import de.seuhd.campuscoffee.domain.ports.IdGenerator
import de.seuhd.campuscoffee.domain.ports.data.ReviewApprovalDataService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Data-layer adapter implementing the review approval data service port. The (review_id, user_id) pair
 * is unique: the database constraint is the authoritative guard for "one approval per user per review",
 * surfaced as a [de.seuhd.campuscoffee.domain.exceptions.DuplicationException].
 */
@Service
class ReviewApprovalDataServiceImpl(
    private val repository: ReviewApprovalRepository,
    private val mapper: ReviewApprovalEntityMapper,
    private val idGenerator: IdGenerator
) : ReviewApprovalDataService {
    private val uniqueConstraint =
        ConstraintMapping<ReviewApproval>(
            { "review ${it.reviewId}, user ${it.userId}" },
            "review_id/user_id",
            ReviewApprovalEntity.REVIEW_USER_UNIQUE_CONSTRAINT
        )

    override fun record(approval: ReviewApproval): ReviewApproval {
        try {
            val entity = mapper.toEntity(approval)
            entity.id = idGenerator.newId()
            return mapper.fromEntity(repository.saveAndFlush(entity))
        } catch (e: DataIntegrityViolationException) {
            val violated = CrudDataServiceImpl.constraintNameOf(e)
            if (violated.equals(uniqueConstraint.constraintName, ignoreCase = true)) {
                throw DuplicationException(
                    ReviewApproval::class.java,
                    uniqueConstraint.columnName,
                    "${uniqueConstraint.extractValue(approval)}"
                )
            }
            throw e
        }
    }

    override fun countByReviewId(reviewId: UUID): Long = repository.countByReviewId(reviewId)

    override fun clear() {
        repository.deleteAllInBatch()
        repository.flush()
    }
}
