package com.storelense.soh.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReceiveTransferRequest(
        @NotEmpty List<String> receivedEpcs
) {}
