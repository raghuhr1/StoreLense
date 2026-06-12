package com.storelense.inventory.dto;

import java.util.List;

public record ReceiveShipmentRequest(List<String> epcs) {}
