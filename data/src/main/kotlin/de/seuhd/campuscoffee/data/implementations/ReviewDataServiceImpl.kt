package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.mapper.ReviewEntityMapper
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import org.springframework.stereotype.Service

/**
 * Data-layer adapter implementing the review data service port. Reviews have no unique constraints,
 * so no constraint mappings are declared.
 */
@Service
class ReviewDataServiceImpl(
    repository: ReviewRepository,
    entityMapper: ReviewEntityMapper,
    private val posEntityMapper: PosEntityMapper,
    private val userEntityMapper: UserEntityMapper,
) : CrudDataServiceImpl<Review, ReviewEntity, ReviewRepository, Long>(
    repository, entityMapper, Review::class.java, emptySet(),
), ReviewDataService {

    override fun filter(pos: Pos, approved: Boolean): List<Review> =
        repository.findAllByPosAndApproved(posEntityMapper.toEntity(pos), approved)
            .map { mapper.fromEntity(it) }

    override fun filter(pos: Pos, author: User): List<Review> =
        repository.findAllByPosAndAuthor(posEntityMapper.toEntity(pos), userEntityMapper.toEntity(author))
            .map { mapper.fromEntity(it) }
}
