package com.storelense.zebra.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StoreDto(
    val id:            String,
    val storeCode:     String,
    val name:          String,
    val city:          String?,
    val stateProvince: String?,
    val countryCode:   String,
    val timezone:      String,
    val active:        Boolean,
)

@JsonClass(generateAdapter = true)
data class ZoneDto(
    val id:           String,
    val storeId:      String,
    val zoneCode:     String,
    val name:         String,
    val zoneType:     String,
    val displayOrder: Int,
    val active:       Boolean,
)

@JsonClass(generateAdapter = true)
data class PageDto<T>(
    val content:       List<T>,
    val page:          Int,
    val size:          Int,
    val totalElements: Long,
    val totalPages:    Int,
    val last:          Boolean,
)
