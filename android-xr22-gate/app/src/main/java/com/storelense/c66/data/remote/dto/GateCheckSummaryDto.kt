package com.storelense.c66.data.remote.dto

data class GateCheckSummaryDto(
    val totalChecks:     Int,
    val released:        Int,
    val flagged:         Int,
    val abandoned:       Int,
    val totalExtraItems: Int,
    val flagRate:        Double
)

data class GateCheckDto(
    val id:            String,
    val storeId:       String,
    val billRef:       String?,
    val checkedAt:     String,
    val expectedCount: Int,
    val matchedCount:  Int,
    val extraCount:    Int,
    val outcome:       String,
    val epcsMatched:   List<String> = emptyList(),
    val epcsExtra:     List<String> = emptyList()
)
