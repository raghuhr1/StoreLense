package com.storelense.store.mapper;

import com.storelense.store.domain.entity.Zone;
import com.storelense.store.dto.ZoneResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ZoneMapper {
    @Mapping(target = "storeId", source = "store.id")
    ZoneResponse toResponse(Zone zone);
}
