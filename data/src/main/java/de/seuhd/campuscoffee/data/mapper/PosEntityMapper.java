package de.seuhd.campuscoffee.data.mapper;

import de.seuhd.campuscoffee.data.persistence.entities.AddressEntity;
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * MapStruct mapper between the {@link Pos} domain model and the {@link PosEntity} persistence entity,
 * including the embedded {@link AddressEntity}. The flat domain address fields are mapped to the embedded
 * entity, and the house number string is split into numeric and suffix parts via {@link HouseNumberConverter}.
 * <p>
 * The address logic is deliberately misaligned between the domain and persistence layers to demonstrate the
 * abstraction that hexagonal architecture provides.
 */
@Mapper(componentModel = "spring")
@ConditionalOnMissingBean // prevent IntelliJ warning about duplicate beans
public abstract class PosEntityMapper implements EntityMapper<Pos, PosEntity> {

    @Autowired
    protected HouseNumberConverter houseNumberConverter;

    @Override
    @Mapping(source = "address.street", target = "street")
    @Mapping(source = "address.postalCode", target = "postalCode")
    @Mapping(source = "address.city", target = "city")
    @Mapping(target = "houseNumber", expression = "java(mergeHouseNumber(source))")
    public abstract Pos fromEntity(PosEntity source);

    @Override
    @Mapping(target = "address", expression = "java(toAddress(source, new AddressEntity()))")
    public abstract PosEntity toEntity(Pos source);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "address", expression = "java(toAddress(source, target.getAddress()))")
    public abstract void updateEntity(Pos source, @MappingTarget PosEntity target);

    /**
     * Merges the numeric house number and suffix stored on the entity into a single string.
     *
     * @param source the entity holding the address; its address may be null
     * @return the merged house number, or null when there is no address or number
     */
    protected String mergeHouseNumber(PosEntity source) {
        if (source.getAddress() == null) {
            return null;
        }
        return houseNumberConverter.merge(
                source.getAddress().getHouseNumber(), source.getAddress().getHouseNumberSuffix());
    }

    /**
     * Copies the address fields from the domain model into the given address entity, splitting the house
     * number string into its numeric and suffix parts. MapStruct only calls this with a non-null source.
     *
     * @param source  the domain model
     * @param address the address entity to populate; null when updating an entity whose embedded address
     *                is absent, in which case a new one is created
     * @return the populated address entity
     */
    protected AddressEntity toAddress(Pos source, AddressEntity address) {
        if (address == null) {
            address = new AddressEntity();
        }
        address.setStreet(source.street());
        address.setCity(source.city());
        address.setPostalCode(source.postalCode());
        HouseNumberConverter.Parts parts = houseNumberConverter.split(source.houseNumber());
        address.setHouseNumber(parts.number());
        address.setHouseNumberSuffix(parts.suffix());
        return address;
    }
}
