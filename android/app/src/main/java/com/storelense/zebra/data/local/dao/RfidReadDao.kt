package com.storelense.zebra.data.local.dao

import androidx.room.*
import com.storelense.zebra.data.local.entity.RfidReadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RfidReadDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)  // IGNORE = deduplicate by sessionId+epc
    suspend fun insertRead(read: RfidReadEntity): Long

    @Query("SELECT COUNT(*) FROM rfid_reads WHERE sessionId = :sessionId")
    fun observeCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(DISTINCT epc) FROM rfid_reads WHERE sessionId = :sessionId")
    fun observeUniqueCount(sessionId: String): Flow<Int>

    @Query("SELECT * FROM rfid_reads WHERE sessionId = :sessionId AND uploaded = 0 LIMIT :limit")
    suspend fun getPendingUploads(sessionId: String, limit: Int = 500): List<RfidReadEntity>

    @Query("UPDATE rfid_reads SET uploaded = 1 WHERE sessionId = :sessionId AND epc IN (:epcs)")
    suspend fun markUploaded(sessionId: String, epcs: List<String>)

    @Query("DELETE FROM rfid_reads WHERE sessionId = :sessionId AND uploaded = 1")
    suspend fun pruneUploaded(sessionId: String)
}
