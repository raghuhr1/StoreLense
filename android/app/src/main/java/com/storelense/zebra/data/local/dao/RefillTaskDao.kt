package com.storelense.zebra.data.local.dao

import androidx.room.*
import com.storelense.zebra.data.local.entity.RefillTaskEntity
import com.storelense.zebra.data.local.entity.RefillTaskItemEntity
import com.storelense.zebra.data.local.entity.RefillTaskWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface RefillTaskDao {

    @Transaction
    @Query("SELECT * FROM refill_tasks WHERE storeId = :storeId ORDER BY priority ASC, createdAt DESC")
    fun observeTasks(storeId: String): Flow<List<RefillTaskWithItems>>

    @Transaction
    @Query("SELECT * FROM refill_tasks WHERE id = :id")
    fun observeTask(id: String): Flow<RefillTaskWithItems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: RefillTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<RefillTaskItemEntity>)

    @Transaction
    suspend fun upsertTaskWithItems(task: RefillTaskEntity, items: List<RefillTaskItemEntity>) {
        upsertTask(task)
        upsertItems(items)
    }

    @Query("UPDATE refill_task_items SET pendingFulfil = :quantity WHERE id = :itemId")
    suspend fun setPendingFulfil(itemId: String, quantity: Int)

    @Query("UPDATE refill_task_items SET fulfilledQuantity = :quantity, pendingFulfil = NULL, status = :status WHERE id = :itemId")
    suspend fun confirmFulfil(itemId: String, quantity: Int, status: String)

    @Query("DELETE FROM refill_tasks WHERE storeId = :storeId AND cachedAt < :olderThan")
    suspend fun pruneOldTasks(storeId: String, olderThan: Long)
}
