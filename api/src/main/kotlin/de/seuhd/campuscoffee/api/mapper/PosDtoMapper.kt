package de.seuhd.campuscoffee.api.mapper

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.domain.model.objects.Pos
import org.mapstruct.Mapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean

/**
 * MapStruct mapper between [Pos] domain objects and [PosDto]s.
 */
@Mapper(componentModel = "spring")
@ConditionalOnMissingBean // prevent IntelliJ warning about duplicate beans
interface PosDtoMapper : DtoMapper<Pos, PosDto>
