package com.storelense.zebra.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rfid_reads",
    foreignKeys = [ForeignKey(
        entity        = SohSessionEntity::class,
        parentColumns = ["id"],
        childColumns  = ["sessionId"],
        onDelete      = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index(value = ["sessionId", "epc"], unique = true)],
)
data class RfidReadEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sessionId:   String,
    val epc:         String,
    val rssi:        Double?,
    val antennaPort: Int?,
    val readAt:      String,
    val uploaded:    Boolean = false,
)
