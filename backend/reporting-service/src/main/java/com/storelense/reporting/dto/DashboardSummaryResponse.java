package com.storelense.reporting.dto;

import java.util.List;

public record DashboardSummaryResponse(
        Float         sohAccuracy,
        List<Float>   accuracyHistory,
        Integer       missingItems,
        List<Integer> missingHistory,
        Integer       ghostTags,
        List<Integer> ghostHistory,
        Integer       readMisses,
        List<Integer> readMissHistory
) {}
