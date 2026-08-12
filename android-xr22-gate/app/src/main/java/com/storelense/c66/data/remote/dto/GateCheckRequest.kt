package com.storelense.c66.data.remote.dto

data class GateCheckRequest(
    val storeId:       String,
    val billRef:       String,
    val expectedCount: Int,
    val matchedCount:  Int,
    val extraCount:    Int,
    val outcome:       String,   // "RELEASED" | "FLAGGED" | "ABANDONED"
    val epcsMatched:   List<String>,
    val epcsExtra:     List<String>
)
