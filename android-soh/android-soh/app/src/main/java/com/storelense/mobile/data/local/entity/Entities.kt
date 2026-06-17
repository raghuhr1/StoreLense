package com.storelense.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epc_reads",
    indices = [Index(value = ["sessionId", "epc"], unique = true)]
)
data class EpcReadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val epc: String,
    val rssi: Double? = null,
    val antennaPort: Int? = null,
    val scannedAt: String,
    val uploaded: Boolean = false
)

@Entity(tableName = "soh_sessions")
data class SohSessionEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val status: String,
    val sessionType: String?,
    val startedAt: String?,
    val source: String = "manual",     // manual | erp_triggered
    val zoneRegion: String? = null,
    val expectedCount: Int = 0,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inbound_reads",
    indices = [Index(value = ["shipmentId", "epc"], unique = true)]
)
data class InboundReadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: String,
    val epc: String,
    val scannedAt: String,
    val uploaded: Boolean = false
)

@Entity(tableName = "inbound_shipments")
data class InboundShipmentEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val dcCode: String?,
    val referenceNumber: String?,
    val status: String,
    val expectedAt: String?,
    val lineCount: Int?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "refill_tasks")
data class RefillTaskEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val status: String,
    val priority: Int,
    val dueBy: String?,
    val itemCount: Int,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "refill_task_items",
    foreignKeys = [ForeignKey(
        entity = RefillTaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class RefillTaskItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val sku: String,
    val productName: String,
    val fromZone: String?,
    val toZone: String?,
    val requiredQty: Int,
    val fulfilledQty: Int,
    val pendingQty: Int = -1
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["erpCode"]),
        Index(value = ["storeId"])
    ]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val sku: String,
    val name: String,
    val description: String?,
    val brand: String?,
    val category: String?,
    val erpCode: String?,
    val storeId: String?,
    val onHandQty: Int = 0,
    val expectedQty: Int = 0,
    val imageUrl: String?,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

// ── Stores ────────────────────────────────────────────────────────────────────

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

// ── Transfers ─────────────────────────────────────────────────────────────────

@Entity(tableName = "transfers_out")
data class TransferOutEntity(
    @PrimaryKey val id: String,
    val destStoreId: String,
    val transferType: String,
    val epcsText: String,          // pipe-joined EPC list: "EPC1|EPC2|EPC3"
    val status: String = "PENDING", // PENDING | SUBMITTED | FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null
)

@Entity(
    tableName = "transfer_manifest",
    primaryKeys = ["transferId", "epc"]
)
data class TransferManifestEntity(
    val transferId: String,
    val epc: String,
    val receivedAt: Long? = null
)

// ── Exceptions ────────────────────────────────────────────────────────────────

@Entity(
    tableName = "exception_cache",
    indices = [Index(value = ["type", "status"])]
)
data class ExceptionCacheEntity(
    @PrimaryKey val epc: String,
    val type: String,              // MISSING_EPC | GHOST_TAG | READ_MISS | UNDER_REVIEW
    val confidence: Int = 0,
    val classification: String?,   // READ_MISS_LIKELY | ACTUALLY_MISSING | GHOST_SUSPECTED | null
    val lastSeen: String?,
    val status: String = "OPEN",   // OPEN | IGNORED | INVESTIGATING | RESOLVED
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ghost_analysis")
data class GhostAnalysisEntity(
    @PrimaryKey val epc: String,
    val confidenceScore: Int = 0,
    val reasonsText: String,       // pipe-joined reason strings: "Single Read|Weak RSSI"
    val status: String,
    val cachedAt: Long = System.currentTimeMillis()
)

