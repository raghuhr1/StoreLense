package com.storelense.zebra.data.local.dao

import androidx.room.*
import com.storelense.zebra.data.local.entity.SohSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SohSessionDao {

    @Query("SELECT * FROM soh_sessions WHERE storeId = :storeId ORDER BY startedAt DESC")
    fun observeSessions(storeId: String): Flow<List<SohSessionEntity>>

    @Query("SELECT * FROM soh_sessions WHERE id = :id")
    suspend fun getSession(id: String): SohSessionEntity?

    @Query("SELECT * FROM soh_sessions WHERE status = 'in_progress' AND storeId = :storeId LIMIT 1")
    suspend fun getActiveSession(storeId: String): SohSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SohSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SohSessionEntity>)

    @Query("UPDATE soh_sessions SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, completedAt: String?)
}
