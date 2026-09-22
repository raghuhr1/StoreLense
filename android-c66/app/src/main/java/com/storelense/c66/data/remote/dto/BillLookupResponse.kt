package com.storelense.c66.data.remote.dto

data class BillLookupItem(
    val ean:           String,
    val productName:   String?,
    val qty:           Int,
    val isRfidEnabled: Boolean? = null,
    val imageUrl:      String? = null
)

data class BillLookupResponse(
    val id:            String,
    val billRef:       String,
    val storeId:       String,
    val createdAt:     String,
    val items:         List<BillLookupItem>,
    val status:        String = "PENDING",
    val gateCheckedAt: String? = null
)
