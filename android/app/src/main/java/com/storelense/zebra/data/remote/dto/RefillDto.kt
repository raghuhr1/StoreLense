package com.storelense.zebra.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RefillTaskDto(
    val id:          String,
    val storeId:     String,
    val taskType:    String,
    val status:      String,
    val priority:    Int,
    val source:      String,
    val dueDate:     String?,
    val notes:       String?,
    val createdBy:   String,
    val createdAt:   String,
    val completedAt: String?,
    val items:       List<RefillTaskItemDto>,
)

@JsonClass(generateAdapter = true)
data class RefillTaskItemDto(
    val id:                 String,
    val productId:          String,
    val zoneId:             String?,
    val requestedQuantity:  Int,
    val fulfilledQuantity:  Int,
    val status:             String,
)
