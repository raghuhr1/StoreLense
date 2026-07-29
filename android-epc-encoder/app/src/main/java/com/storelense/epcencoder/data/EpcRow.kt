package com.storelense.epcencoder.data

enum class RowStatus {
    PENDING,
    WRITTEN,
    WRITE_OK_REG_FAILED,
    FAILED,
    SKIPPED
}

data class EpcRow(
    val srNo: String,
    val targetEpc: String,
    val ean: String,
    val dept: String,
    val style: String,
    val description: String,
    val color: String,
    val size: String,
    var status: RowStatus = RowStatus.PENDING,
    var productId: String? = null,
    var errorMessage: String? = null
)
