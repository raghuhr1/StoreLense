package com.storelense.zebra.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soh_sessions")
data class SohSessionEntity(
    @PrimaryKey val id:            String,
    val storeId:        String,
    val zoneId:         String?,
    val sessionType:    String,
    val status:         String,
    val startedBy:      String,
    val startedAt:      String,
    val completedAt:    String?,
    val totalEpcReads:  Int,
    val uniqueEpcCount: Int,
    val notes:          String?,
    val synced:         Boolean = true,
)
