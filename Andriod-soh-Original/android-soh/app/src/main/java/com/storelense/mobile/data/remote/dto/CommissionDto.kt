package com.storelense.mobile.data.remote.dto

data class CommissionRequest(
    val storeId: String,
    val sku: String,
    val epc: String,
    val zone: String,
    // Only set when explicitly retagging a specific broken/lost tag — left null for
    // the normal case of tagging a genuinely new physical unit.
    val replacesEpc: String? = null
)

data class CommissionResponseDto(
    val epc: String,
    val sku: String,
    val productName: String,
    val productId: String,
    val storeId: String,
    val zone: String,
    val totalTaggedInStore: Int
)
