package com.storelense.mobile.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Common wrappers ───────────────────────────────────────────────────────────

data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null      // maps to backend ApiResponse.code (e.g. "ZONE_TAKEN")
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
    val userId: String,
    val username: String,
    val role: String,
    val storeId: String?
)

// ── SOH ───────────────────────────────────────────────────────────────────────

data class CreateSohSessionRequest(
    val storeId: String,
    val sessionType: String = "full_store",
    val notes: String? = null,
    val source: String? = null,
    val zoneRegion: String? = null
)

data class SohSessionDto(
    val id: String,
    val storeId: String,
    val status: String,
    val sessionType: String?,
    val expectedEpcs: List<String>? = null,
    val result: SohResultDto?,
    val startedAt: String?,
    val completedAt: String?,
    val source: String = "manual",
    val zoneRegion: String? = null
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
    val deviceId: String,
    val reads: List<RfidReadDto>
)

data class RfidReadDto(
    val epc: String,
    val rssi: Double?,
    val antennaPort: Int?,
    val readAt: String?,
    val zoneId: String? = null
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
    @SerializedName("categoryId") val category: String?,   // backend sends UUID as categoryId
    @SerializedName("erpProductCode") val erpCode: String?, // backend field name is erpProductCode
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

// ── Dashboard ─────────────────────────────────────────────────────────────────

data class DashboardSummaryDto(
    val sohAccuracy: Float = 0f,
    val accuracyHistory: List<Float> = emptyList(),
    val missingItems: Int = 0,
    val missingHistory: List<Int> = emptyList(),
    val ghostTags: Int = 0,
    val ghostHistory: List<Int> = emptyList(),
    val readMisses: Int = 0,
    val readMissHistory: List<Int> = emptyList(),
    val scannedEpcsToday: Int = 0,
    val receivedShipmentsToday: Int = 0,
    val transferredEpcsToday: Int = 0,
    val pendingReplenishments: Int = 0,
    val pendingInbound: Int = 0,
    val pendingTransfers: Int = 0
)

// ── Stores & Zones ────────────────────────────────────────────────────────────

data class StoreDto(
    val id: String,
    val name: String,
    @SerializedName("store_code") val code: String?
)

data class ZoneDto(
    val id: String,
    val storeId: String,
    val name: String,
    val zoneCode: String?,
    val zoneType: String?,
    val displayOrder: Int = 0,
    val active: Boolean = true
)

// ── Transfers ─────────────────────────────────────────────────────────────────

data class CreateTransferRequest(
    val sourceStoreId: String,
    val destStoreId: String,
    val transferType: String,
    val epcs: List<String>
)

data class TransferDto(
    val id: String,
    val sourceStoreId: String,
    val destStoreId: String,
    val transferType: String,
    val status: String,
    val epcs: List<String> = emptyList(),
    val createdAt: String?
)

data class ReceiveTransferRequest(val receivedEpcs: List<String>)

// ── Exceptions ────────────────────────────────────────────────────────────────

data class ExceptionSummaryDto(
    val missingEpcs: Int = 0,
    val ghostTags: Int = 0,
    val readMisses: Int = 0,
    val underReview: Int = 0
)

data class ExceptionItemDto(
    val epc: String,
    val type: String,
    val confidence: Int,
    val classification: String?,
    val lastSeen: String?,
    val status: String
)

data class GhostAnalysisDetailDto(
    val epc: String,
    val status: String,
    val confidenceScore: Int,
    val reasons: List<String> = emptyList(),
    val firstSeen: String?,
    val lastSeen: String?
)

data class MissingEpcDetailDto(
    val epc: String,
    val sku: String?,
    val productName: String?,
    val lastSeen: String?,
    val confidenceScore: Int,
    val classification: String   // READ_MISS_LIKELY | ACTUALLY_MISSING
)

// ── Inventory state (store-specific product list + quantities) ────────────────

data class InventoryStateDto(
    val productId: String,
    val quantityOnHand: Int = 0,
    val quantityExpected: Int = 0
)

// ── Inventory Lookup ──────────────────────────────────────────────────────────

data class InventorySkuDto(
    val onFloor: Int = 0,
    val inBackroom: Int = 0,
    val total: Int = 0,
    val epcs: List<String> = emptyList()
)

// ── Phase 5: Session Participants ─────────────────────────────────────────────

data class JoinSessionRequest(
    val deviceId: String,
    val zoneRegion: String? = null
)

data class ParticipantDto(
    val id: String,
    val deviceId: String,
    val zoneRegion: String?,
    val status: String,
    val joinedAt: String?,
    val completedAt: String?
)

data class MarkDoneDto(
    val status: String,
    val isLastActive: Boolean,
    val activeCount: Int
)

data class ParticipantsListDto(
    val participants: List<ParticipantDto> = emptyList(),
    val activeCount: Int = 0,
    val doneCount: Int = 0
)
