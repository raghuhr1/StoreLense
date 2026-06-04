package com.storelense.zebra.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateSohSessionRequest(
    val storeId:     String,
    val zoneId:      String?,
    val sessionType: String,
    val notes:       String? = null,
)

@JsonClass(generateAdapter = true)
data class SohSessionDto(
    val id:             String,
    val storeId:        String,
    val zoneId:         String?,
    val sessionType:    String,
    val status:         String,
    val startedBy:      String,
    val startedAt:      String,
    val completedAt:    String?,
    val totalEpcReads:  Int,
    val uniqueEpcCount: Int,
    val notes:          String?,
)

@JsonClass(generateAdapter = true)
data class SohResultDto(
    val id:                   String,
    val sessionId:            String,
    val storeId:              String,
    val totalProductsCounted: Int,
    val totalUnitsCounted:    Int,
    val totalUnitsExpected:   Int,
    val accuracyPct:          Double,
    val varianceCount:        Int,
    val overcountItems:       Int,
    val undercountItems:      Int,
    val resultGeneratedAt:    String,
)
