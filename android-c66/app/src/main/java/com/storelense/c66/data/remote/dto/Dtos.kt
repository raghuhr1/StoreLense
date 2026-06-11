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
    val user: UserDto
)

data class UserDto(
    val id: String,
    val username: String,
    val roles: List<String>,
    val storeId: String?
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
    val epcs: List<String> = emptyList()
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
