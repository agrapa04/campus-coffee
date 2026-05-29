package de.seuhd.campuscoffee.data.mapper

import de.seuhd.campuscoffee.data.persistence.entities.AddressEntity
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.domain.model.objects.Pos
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean

/**
 * MapStruct mapper between the [Pos] domain model and the [PosEntity] entity, including the embedded
 * [AddressEntity]. The flat domain address fields map to the embedded entity, and the house number
 * string is split into numeric and suffix parts via [HouseNumberConverter]. The address layout is
 * deliberately misaligned between the layers to demonstrate the abstraction hexagonal architecture
 * provides.
 */
@Mapper(componentModel = "spring")
@ConditionalOnMissingBean // prevent IntelliJ warning about duplicate beans
abstract class PosEntityMapper : EntityMapper<Pos, PosEntity> {
    @Autowired
    protected lateinit var houseNumberConverter: HouseNumberConverter

    @Mapping(source = "address.street", target = "street")
    @Mapping(source = "address.postalCode", target = "postalCode")
    @Mapping(source = "address.city", target = "city")
    @Mapping(target = "houseNumber", expression = "java(mergeHouseNumber(source))")
    abstract override fun fromEntity(source: PosEntity): Pos

    @Mapping(target = "address", expression = "java(toAddress(source, new AddressEntity()))")
    abstract override fun toEntity(source: Pos): PosEntity

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "address", expression = "java(toAddress(source, target.getAddress()))")
    abstract override fun updateEntity(
        source: Pos,
        @MappingTarget target: PosEntity
    )

    /**
     * Merges the numeric house number and suffix stored on the entity into a single string.
     *
     * @return the merged house number, or null when there is no address or number
     */
    protected fun mergeHouseNumber(source: PosEntity): String? {
        val address = source.address ?: return null
        return houseNumberConverter.merge(address.houseNumber, address.houseNumberSuffix)
    }

    /**
     * Copies the address fields from the domain model into the address entity, splitting the house
     * number string into its numeric and suffix parts. MapStruct only calls this with a non-null source;
     * a null address (an update of an entity whose embedded address is absent) yields a new one.
     */
    protected fun toAddress(
        source: Pos,
        address: AddressEntity?
    ): AddressEntity {
        val target = address ?: AddressEntity()
        target.street = source.street
        target.city = source.city
        target.postalCode = source.postalCode
        val parts = houseNumberConverter.split(source.houseNumber)
        target.houseNumber = parts.number
        target.houseNumberSuffix = parts.suffix
        return target
    }
}
