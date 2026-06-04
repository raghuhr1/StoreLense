package com.storelense.store.dto;

public record UpdateZoneRequest(String name, String zoneType, Integer displayOrder, Boolean active) {}
