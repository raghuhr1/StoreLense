package com.storelense.zebra.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "refill_tasks")
data class RefillTaskEntity(
    @PrimaryKey val id:  String,
    val storeId:         String,
    val taskType:        String,
    val status:          String,
    val priority:        Int,
    val source:          String,
    val dueDate:         String?,
    val notes:           String?,
    val createdBy:       String,
    val createdAt:       String,
    val completedAt:     String?,
    val cachedAt:        Long = System.currentTimeMillis(),
)

@Entity(tableName = "refill_task_items")
data class RefillTaskItemEntity(
    @PrimaryKey val id:       String,
    val taskId:               String,
    val productId:            String,
    val zoneId:               String?,
    val requestedQuantity:    Int,
    val fulfilledQuantity:    Int,
    val status:               String,
    val pendingFulfil:        Int? = null,  // local optimistic update not yet synced
)

data class RefillTaskWithItems(
    @Embedded val task: RefillTaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId",
    )
    val items: List<RefillTaskItemEntity>,
)
