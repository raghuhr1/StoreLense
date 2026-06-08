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

