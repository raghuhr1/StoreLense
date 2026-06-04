package com.storelense.store.dto;

import java.util.UUID;

public record ZoneResponse(UUID id, UUID storeId, String zoneCode, String name, String zoneType, int displayOrder, boolean active) {}
