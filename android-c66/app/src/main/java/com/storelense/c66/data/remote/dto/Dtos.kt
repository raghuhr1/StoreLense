package com.storelense.c66.data.remote.dto

data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null
)

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(val username: String, val password: String)

data class LoginData(
    val accessToken: String,
    val refreshToken: String,
    val userId:       String,
    val username:     String,
    val role:         String,
    val storeId:      String?
)

// ── Gate / EAN resolution ─────────────────────────────────────────────────────

/**
 * Response from GET /api/inventory/epc-by-ean/{ean}?storeId=...
 * Returns every EPC currently marked in_store for that EAN at that store.
 */
data class EpcsByEanResponse(
    val ean: String,
    val sku: String?,
    val productName: String,
    val epcs: List<String> = emptyList(),
    val imageUrl: String? = null
)

// ── Mark sold ─────────────────────────────────────────────────────────────────

data class MarkEpcsSoldRequest(
    val storeId: String,
    val epcs: List<String>
)

data class MarkEpcsSoldResponse(
    val marked: Int,
    val total: Int,
    val notFound: Int
)

/** Non-RFID items verified by barcode at the gate — no EPC exists, so sale is recorded
 *  by EAN + quantity instead of by tag. */
data class MarkNonRfidSoldRequest(
    val storeId: String,
    val items: List<NonRfidSaleItem>
)

data class NonRfidSaleItem(
    val ean: String,
    val qty: Int
)

// ── Store features ────────────────────────────────────────────────────────────

data class StoreFeatureDto(
    val feature: String,
    val enabled: Boolean
)

// ── Identify EPC (extra/unexpected tag lookup) ────────────────────────────────

data class IdentifyEpcResponse(
    val epc: String,
    val productId: String?,
    val sku: String?,
    val productName: String?,
    val eans: List<String> = emptyList(),
    val statusInStore: String?,
    val zoneName: String?,
    val alreadyRegistered: Boolean = false,
    val imageUrl: String? = null
)
