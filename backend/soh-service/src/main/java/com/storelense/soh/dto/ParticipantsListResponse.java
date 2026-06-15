package com.storelense.soh.dto;

import java.util.List;

public record ParticipantsListResponse(
        List<ParticipantResponse> participants,
        int activeCount,
        int doneCount
) {}
