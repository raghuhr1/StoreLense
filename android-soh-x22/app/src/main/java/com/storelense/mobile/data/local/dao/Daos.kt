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

    @Query("SELECT COUNT(*) FROM epc_reads WHERE uploaded = 0")
    fun countAllPendingFlow(): Flow<Int>
}

@Dao
interface SohSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SohSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SohSessionEntity>)

    @Query("DELETE FROM soh_sessions WHERE storeId = :storeId")
    suspend fun deleteForStore(storeId: String)

    @Query("SELECT * FROM soh_sessions WHERE storeId = :storeId ORDER BY cachedAt DESC")
    fun getForStore(storeId: String): Flow<List<SohSessionEntity>>

    @Query("SELECT * FROM soh_sessions WHERE id = :id")
    suspend fun getById(id: String): SohSessionEntity?

    @Query("DELETE FROM soh_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
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
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("SELECT * FROM products WHERE storeId = :storeId ORDER BY name ASC")
    fun getAllForStore(storeId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Query("""
        SELECT * FROM products
        WHERE storeId = :storeId
          AND (name LIKE '%' || :query || '%'
            OR sku LIKE '%' || :query || '%'
            OR brand LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR erpCode LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%')
        ORDER BY
          CASE WHEN name LIKE :query || '%' THEN 0
               WHEN sku LIKE :query || '%'  THEN 1
               ELSE 2 END,
          name ASC
        LIMIT 50
    """)
    suspend fun search(query: String, storeId: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE (sku = :epc OR erpCode = :epc) AND storeId = :storeId LIMIT 1")
    suspend fun getByEpc(epc: String, storeId: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products WHERE storeId = :storeId")
    suspend fun countForStore(storeId: String): Int

    @Query("SELECT MAX(lastSyncedAt) FROM products WHERE storeId = :storeId")
    suspend fun getMaxLastSynced(storeId: String): Long?

    @Query("DELETE FROM products WHERE storeId = :storeId")
    suspend fun deleteForStore(storeId: String)
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

    @Query("""
        SELECT ri.* FROM refill_task_items ri
        INNER JOIN refill_tasks rt ON ri.taskId = rt.id
        WHERE rt.storeId = :storeId AND rt.status != 'completed'
    """)
    fun getAllItemsForStore(storeId: String): Flow<List<RefillTaskItemEntity>>
}

// ── Stores ────────────────────────────────────────────────────────────────────

@Dao
interface StoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StoreEntity>)

    @Query("SELECT * FROM stores ORDER BY name ASC")
    fun getAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores ORDER BY name ASC")
    suspend fun getAllSync(): List<StoreEntity>

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun count(): Int

    @Query("DELETE FROM stores")
    suspend fun deleteAll()
}

// ── Transfers ─────────────────────────────────────────────────────────────────

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransferOutEntity)

    @Query("SELECT * FROM transfers_out WHERE id = :id")
    suspend fun getById(id: String): TransferOutEntity?

    @Query("SELECT * FROM transfers_out WHERE sourceStoreId = :storeId ORDER BY createdAt DESC")
    fun getForStore(storeId: String): Flow<List<TransferOutEntity>>

    @Query("UPDATE transfers_out SET status = :status, uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, uploadedAt: Long? = null)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManifestItems(entities: List<TransferManifestEntity>)

    @Query("SELECT * FROM transfer_manifest WHERE transferId = :transferId")
    suspend fun getManifest(transferId: String): List<TransferManifestEntity>

    @Query("UPDATE transfer_manifest SET receivedAt = :receivedAt WHERE transferId = :transferId AND epc = :epc")
    suspend fun markEpcReceived(transferId: String, epc: String, receivedAt: Long)

    @Query("SELECT COUNT(*) FROM transfers_out WHERE uploadedAt IS NULL")
    fun countPendingFlow(): Flow<Int>
}

// ── Exceptions ────────────────────────────────────────────────────────────────

@Dao
interface ExceptionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ExceptionCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExceptionCacheEntity)

    @Query("SELECT * FROM exception_cache WHERE type = :type AND storeId = :storeId ORDER BY confidence DESC")
    fun getByType(type: String, storeId: String): Flow<List<ExceptionCacheEntity>>

    @Query("SELECT * FROM exception_cache WHERE type = :type AND storeId = :storeId ORDER BY confidence DESC")
    suspend fun getByTypeSync(type: String, storeId: String): List<ExceptionCacheEntity>

    @Query("SELECT * FROM exception_cache WHERE epc = :epc")
    suspend fun getByEpc(epc: String): ExceptionCacheEntity?

    @Query("UPDATE exception_cache SET status = :status WHERE epc = :epc")
    suspend fun updateStatus(epc: String, status: String)
}

// ── Ghost Analysis ────────────────────────────────────────────────────────────

@Dao
interface GhostAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GhostAnalysisEntity)

    @Query("SELECT * FROM ghost_analysis WHERE epc = :epc")
    suspend fun getByEpc(epc: String): GhostAnalysisEntity?
}
