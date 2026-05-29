package de.seuhd.campuscoffee.data.mapper

import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.domain.model.objects.Review
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean

/**
 * MapStruct mapper between [Review] domain objects and [ReviewEntity] persistence entities; delegates
 * the POS and author mapping to the POS and user entity mappers.
 */
@Mapper(
    componentModel = "spring",
    uses = [PosEntityMapper::class, UserEntityMapper::class]
)
@ConditionalOnMissingBean // prevent IntelliJ warning about duplicate beans
interface ReviewEntityMapper : EntityMapper<Review, ReviewEntity> {
    @Mapping(target = "version", ignore = true)
    override fun toEntity(source: Review): ReviewEntity

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "approvalCount", defaultValue = "0")
    override fun updateEntity(
        source: Review,
        @MappingTarget target: ReviewEntity
    )
}
