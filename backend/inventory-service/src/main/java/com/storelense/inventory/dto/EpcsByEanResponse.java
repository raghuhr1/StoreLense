package com.storelense.inventory.dto;

import java.util.List;

public record EpcsByEanResponse(
        String       ean,
        String       sku,
        String       productName,
        List<String> epcs
) {}
