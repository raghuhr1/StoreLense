package com.storelense.product.dto;

import java.util.UUID;

public record EpcLookupResponse(String epc, UUID productId, boolean fromCache) {}
