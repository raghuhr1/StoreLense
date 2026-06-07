package com.storelense.mobile.data.local.dao

import androidx.room.*
import com.storelense.mobile.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EpcReadDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: EpcReadEntity): Long

    @Query("SELECT * FROM epc_reads WHERE sessionId = :sessionId AND uploaded = 0")
    suspend fun getPending(sessionId: String): List<EpcReadEntity>

    @Query("SELECT COUNT(*) FROM epc_reads WHERE sessionId = :sessionId")
    fun countFlow(sessionId: String): Flow<Int>

    @Query("UPDATE epc_reads SET uploaded = 1 WHERE sessionId = :sessionId")
    suspend fun markAllUploaded(sessionId: String)

    @Query("DELETE FROM epc_reads WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}

@Dao
interface SohSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SohSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SohSessionEntity>)

    @Query("SELECT * FROM soh_sessions WHERE storeId = :storeId ORDER BY cachedAt DESC")
    fun getForStore(storeId: String): Flow<List<SohSessionEntity>>

    @Query("SELECT * FROM soh_sessions WHERE id = :id")
    suspend fun getById(id: String): SohSessionEntity?
}

@Dao
interface InboundReadDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: InboundReadEntity): Long

    @Query("SELECT * FROM inbound_reads WHERE shipmentId = :shipmentId AND uploaded = 0")
    suspend fun getPending(shipmentId: String): List<InboundReadEntity>

    @Query("SELECT COUNT(*) FROM inbound_reads WHERE shipmentId = :shipmentId")
    fun countFlow(shipmentId: String): Flow<Int>

    @Query("UPDATE inbound_reads SET uploaded = 1 WHERE shipmentId = :shipmentId")
    suspend fun markAllUploaded(shipmentId: String)
}

@Dao
interface InboundShipmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InboundShipmentEntity>)

    @Query("SELECT * FROM inbound_shipments WHERE storeId = :storeId ORDER BY cachedAt DESC")
    fun getForStore(storeId: String): Flow<List<InboundShipmentEntity>>

    @Query("SELECT * FROM inbound_shipments WHERE id = :id")
    suspend fun getById(id: String): InboundShipmentEntity?
}

@Dao
interface RefillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(entities: List<RefillTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(entities: List<RefillTaskItemEntity>)

    @Query("SELECT * FROM refill_tasks WHERE storeId = :storeId ORDER BY priority DESC, dueBy ASC")
    fun getTasksForStore(storeId: String): Flow<List<RefillTaskEntity>>

    @Query("SELECT * FROM refill_task_items WHERE taskId = :taskId")
    fun getItemsForTask(taskId: String): Flow<List<RefillTaskItemEntity>>

    @Query("UPDATE refill_task_items SET pendingQty = :qty WHERE id = :itemId")
    suspend fun setPendingQty(itemId: String, qty: Int)

    @Query("UPDATE refill_task_items SET fulfilledQty = :qty, pendingQty = -1 WHERE id = :itemId")
    suspend fun confirmFulfil(itemId: String, qty: Int)

    @Query("UPDATE refill_tasks SET status = 'completed' WHERE id = :taskId")
    suspend fun markCompleted(taskId: String)
}
