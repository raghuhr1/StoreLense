package com.storelense.store.dto;

import java.util.Map;

public record UpdateFeaturesRequest(Map<String, Boolean> features) {}
