package com.storelense.mobile.data.local.entity

import androidx.room.ColumnInfo
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
    val uploaded: Boolean = false,
    val zoneId: String? = null,
    /** SALES_FLOOR or BACKROOM — set at scan time from the parent session. */
    val locationCode: String? = null,
    /** MENS | WOMENS | KIDS | FOOTWEAR | ACCESSORIES — null for BACKROOM reads. */
    val sectionCode: String? = null
)

@Entity(tableName = "soh_sessions")
data class SohSessionEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val status: String,
    val sessionType: String?,
    val startedAt: String?,
    @ColumnInfo(defaultValue = "manual") val source: String = "manual",     // manual | erp_triggered
    val zoneRegion: String? = null,
    @ColumnInfo(defaultValue = "0") val expectedCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val cachedAt: Long = 0,
    /** SALES_FLOOR or BACKROOM */
    val locationCode: String? = null,
    /** MENS | WOMENS | KIDS | FOOTWEAR | ACCESSORIES — null for BACKROOM sessions. */
    val sectionCode: String? = null,
    /** UUID of the parent cycle count grouping this session with others. */
    val cycleCountId: String? = null
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
    @ColumnInfo(defaultValue = "0") val cachedAt: Long = 0
)

@Entity(tableName = "refill_tasks")
data class RefillTaskEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val status: String,
    val priority: Int,
    val dueBy: String?,
    val itemCount: Int,
    @ColumnInfo(defaultValue = "0") val cachedAt: Long = 0
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
        Index(value = ["sku"]),          // not unique — same SKU can exist across stores
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
    @ColumnInfo(defaultValue = "0") val onHandQty: Int = 0,
    @ColumnInfo(defaultValue = "0") val expectedQty: Int = 0,
    val imageUrl: String?,
    @ColumnInfo(defaultValue = "0") val lastSyncedAt: Long = System.currentTimeMillis()
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
    @ColumnInfo(defaultValue = "") val sourceStoreId: String = "",
    val destStoreId: String,
    val transferType: String,
    val epcsText: String,          // pipe-joined EPC list: "EPC1|EPC2|EPC3"
    @ColumnInfo(defaultValue = "PENDING") val status: String = "PENDING", // PENDING | SUBMITTED | FAILED
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
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
    @ColumnInfo(defaultValue = "") val storeId: String = "",
    val type: String,              // MISSING_EPC | GHOST_TAG | READ_MISS | UNDER_REVIEW
    @ColumnInfo(defaultValue = "0") val confidence: Int = 0,
    val classification: String?,   // READ_MISS_LIKELY | ACTUALLY_MISSING | GHOST_SUSPECTED | null
    val lastSeen: String?,
    @ColumnInfo(defaultValue = "OPEN") val status: String = "OPEN",   // OPEN | IGNORED | INVESTIGATING | RESOLVED
    @ColumnInfo(defaultValue = "0") val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ghost_analysis")
data class GhostAnalysisEntity(
    @PrimaryKey val epc: String,
    @ColumnInfo(defaultValue = "0") val confidenceScore: Int = 0,
    val reasonsText: String,       // pipe-joined reason strings: "Single Read|Weak RSSI"
    val status: String,
    @ColumnInfo(defaultValue = "0") val cachedAt: Long = System.currentTimeMillis()
)

