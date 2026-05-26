package de.seuhd.campuscoffee.api.mapper;

import de.seuhd.campuscoffee.api.dtos.ReviewDto;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.ports.api.PosService;
import de.seuhd.campuscoffee.domain.ports.api.UserService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Mapper between the {@link Review} domain model and {@link ReviewDto}. {@code fromDomain} is generated
 * by MapStruct from the {@code @Mapping} annotations below; {@code toDomain} is hand-written because it
 * resolves the referenced POS and author through the services and resets the approval state.
 */
@Mapper(componentModel = "spring")
@ConditionalOnMissingBean // prevent IntelliJ warning about duplicate beans
public abstract class ReviewDtoMapper implements DtoMapper<Review, ReviewDto> {
    @Autowired
    protected PosService posService;
    @Autowired
    protected UserService userService;

    @Override
    @Mapping(target = "posId", source = "pos.id")
    @Mapping(target = "authorId", source = "author.id")
    public abstract ReviewDto fromDomain(Review source);

    /**
     * Builds a domain review from the DTO, resolving the referenced POS and author by id. A new review
     * always starts unapproved with a zero approval count; the DTO's {@code approved} value is ignored.
     *
     * @param source the review DTO
     * @return the domain review with resolved references and reset approval state
     */
    @Override
    public Review toDomain(ReviewDto source) {
        return Review.builder()
                .id(source.id())
                .createdAt(source.createdAt())
                .updatedAt(source.updatedAt())
                .pos(posService.getById(source.posId()))
                .author(userService.getById(source.authorId()))
                .review(source.review())
                .approved(false)
                .approvalCount(0)
                .build();
    }
}
