package com.storelense.store.mapper;

import com.storelense.store.domain.entity.Store;
import com.storelense.store.dto.CreateStoreRequest;
import com.storelense.store.dto.StoreResponse;
import com.storelense.store.dto.UpdateStoreRequest;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StoreMapper {
    StoreResponse toResponse(Store store);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "zones", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Store toEntity(CreateStoreRequest req);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "storeCode", ignore = true)
    @Mapping(target = "zones", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateStoreRequest req, @MappingTarget Store store);
}
