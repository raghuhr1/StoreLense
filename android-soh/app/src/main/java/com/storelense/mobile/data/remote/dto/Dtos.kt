package com.storelense.mobile.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Common wrappers ───────────────────────────────────────────────────────────

data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null
)

data class PagedData<T>(
    val content: List<T> = emptyList(),
    val totalElements: Int = 0,
    val totalPages: Int = 0,
    val page: Int = 0,
    val size: Int = 20
)

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(val username: String, val password: String)

data class RefreshRequest(val refreshToken: String)

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

// ── SOH ───────────────────────────────────────────────────────────────────────

data class CreateSohSessionRequest(
    val storeId: String,
    val sessionType: String = "full_store",
    val notes: String? = null
)

data class SohSessionDto(
    val id: String,
    val storeId: String,
    val status: String,
    val sessionType: String?,
    val expectedEpcs: List<String>?,
    val result: SohResultDto?,
    val startedAt: String?,
    val completedAt: String?
)

data class SohResultDto(
    val accuracyPct: Double,
    val totalUnitsCounted: Int,
    val totalUnitsExpected: Int,
    val varianceCount: Int,
    val overcountItems: Int,
    val undercountItems: Int
)

data class RfidBatchRequest(
    val rfidSessionId: String,
    val storeId: String,
    val deviceId: String = "zebra-handheld",
    val reads: List<RfidReadDto>
)

data class RfidReadDto(
    val epc: String,
    val rssi: Double?,
    val antennaPort: Int?
)

// ── Inbound ───────────────────────────────────────────────────────────────────

data class InboundShipmentDto(
    val id: String,
    val storeId: String,
    val dcCode: String?,
    val referenceNumber: String?,
    val status: String,           // expected, receiving, received
    val expectedAt: String?,
    val expectedEpcs: List<String>?,
    val lineCount: Int?
)

data class InboundReceiveRequest(val scannedEpcs: List<String>)

data class InboundResultDto(
    val receivedCount: Int,
    val expectedCount: Int,
    val shortageCount: Int,
    val surplusCount: Int
)

// ── Replenishment ─────────────────────────────────────────────────────────────

data class RefillTaskDto(
    val id: String,
    val storeId: String,
    val status: String,           // pending, in_progress, completed
    val priority: Int,
    val dueBy: String?,
    val itemCount: Int,
    val items: List<RefillTaskItemDto>?
)

data class RefillTaskItemDto(
    val id: String,
    val taskId: String,
    val sku: String,
    val productName: String,
    val fromZone: String?,
    val toZone: String?,
    val requiredQty: Int,
    val fulfilledQty: Int
)

data class FulfilItemRequest(
    val fulfilledQty: Int,
    val scannedEpcs: List<String>? = null
)

// ── Products ──────────────────────────────────────────────────────────────────

data class ProductDto(
    val id: String,
    val sku: String,
    val name: String,
    val description: String?,
    val brand: String?,
    val category: String?,
    @SerializedName("erp_code") val erpCode: String?,
    val storeId: String?,
    val onHandQty: Int = 0,
    val expectedQty: Int = 0,
    val imageUrl: String?
)

// ── Item Locator ──────────────────────────────────────────────────────────────

data class EpcLocationDto(
    val epc: String,
    val zone: String?,
    val lastSeenAt: String?,
    val storeId: String?
)
