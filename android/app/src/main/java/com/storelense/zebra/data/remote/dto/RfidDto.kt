package com.storelense.zebra.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RfidReadBatchRequest(
    val rfidSessionId: String,
    val storeId:       String,
    val deviceId:      String,
    val readerId:      String?,
    val reads:         List<RfidReadRequest>,
)

@JsonClass(generateAdapter = true)
data class RfidReadRequest(
    val epc:         String,
    val rssi:        Double?,
    val antennaPort: Int?,
    val readAt:      String,
)

@JsonClass(generateAdapter = true)
data class RfidBatchResponse(
    val message:   String,
    val published: Int,
)

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean,
    val code:    String,
    val message: String?,
    val data:    T?,
)
