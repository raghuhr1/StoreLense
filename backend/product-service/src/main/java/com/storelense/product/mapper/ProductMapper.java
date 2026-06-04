package com.storelense.product.mapper;

import com.storelense.product.domain.entity.Product;
import com.storelense.product.dto.CreateProductRequest;
import com.storelense.product.dto.ProductResponse;
import com.storelense.product.dto.UpdateProductRequest;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "epcTags", ignore = true)
    @Mapping(target = "barcodes", ignore = true)
    @Mapping(target = "erpSyncedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(CreateProductRequest req);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "epcTags", ignore = true)
    @Mapping(target = "barcodes", ignore = true)
    @Mapping(target = "erpSyncedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateProductRequest req, @MappingTarget Product product);
}
