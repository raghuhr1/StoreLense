package com.storelense.c66.data.remote.dto

data class BillLookupItem(
    val ean:         String,
    val productName: String?,
    val qty:         Int
)

data class BillLookupResponse(
    val id:        String,
    val billRef:   String,
    val storeId:   String,
    val createdAt: String,
    val items:     List<BillLookupItem>
)
