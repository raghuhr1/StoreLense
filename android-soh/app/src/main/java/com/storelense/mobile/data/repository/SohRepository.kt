package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.EpcReadDao
import com.storelense.mobile.data.local.dao.SohSessionDao
import com.storelense.mobile.data.local.entity.EpcReadEntity
import com.storelense.mobile.data.local.entity.SohSessionEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.TokenManager
import com.storelense.mobile.data.remote.dto.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SohRepository @Inject constructor(
    private val api: ApiService,
    private val sessionDao: SohSessionDao,
    private val epcReadDao: EpcReadDao,
    private val tokenManager: TokenManager
) {
    fun sessionsFlow(storeId: String): Flow<List<SohSessionEntity>> =
        sessionDao.getForStore(storeId)

    suspend fun refreshSessions(storeId: String): Result<Unit> = try {
        val resp = api.getSohSessions(storeId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            val sessions = body.data?.content ?: emptyList()
            sessionDao.upsertAll(sessions.map { it.toEntity() })
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Failed to load sessions")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getSession(id: String): Result<SohSessionDto> = try {
        val resp = api.getSohSession(id)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            body.data.let { sessionDao.upsert(it.toEntity()) }
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Session not found")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun createSession(storeId: String): Result<SohSessionDto> = try {
        val resp = api.createSohSession(CreateSohSessionRequest(storeId))
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            sessionDao.upsert(body.data.toEntity())
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Failed to create session")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun bufferEpc(sessionId: String, epc: String, rssi: Double?, antenna: Int?) {
        epcReadDao.insert(
            EpcReadEntity(
                sessionId   = sessionId,
                epc         = epc,
                rssi        = rssi,
                antennaPort = antenna,
                scannedAt   = Instant.now().toString()
            )
        )
    }

    fun epcCountFlow(sessionId: String): Flow<Int> = epcReadDao.countFlow(sessionId)

    suspend fun getPendingEpcs(sessionId: String): List<String> =
        epcReadDao.getPending(sessionId).map { it.epc }

    // Fix #13: Stub — returns 1 (only this device) until Phase 5 adds the session-participants endpoint.
    suspend fun getActiveDevices(sessionId: String): Int = 1

    suspend fun uploadBatch(sessionId: String, storeId: String): Result<Unit> = try {
        val pending = epcReadDao.getPending(sessionId)
        if (pending.isEmpty()) return Result.Success(Unit)
        val CHUNK = 300
        for (i in pending.indices step CHUNK) {
            val chunk = pending.subList(i, minOf(i + CHUNK, pending.size))
            val req = RfidBatchRequest(
                rfidSessionId = sessionId,
                storeId       = storeId,
                reads         = chunk.map { RfidReadDto(it.epc, it.rssi, it.antennaPort) }
            )
            val resp = api.ingestRfidBatch(req)
            if (!resp.isSuccessful) return Result.Error("Upload failed at chunk $i")
        }
        epcReadDao.markAllUploaded(sessionId)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Upload error")
    }

    suspend fun completeSession(sessionId: String): Result<SohSessionDto> = try {
        val resp = api.completeSohSession(sessionId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Complete failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    private fun SohSessionDto.toEntity() = SohSessionEntity(
        id          = id,
        storeId     = storeId,
        status      = status,
        sessionType = sessionType,
        startedAt   = startedAt,
        source      = source,
        zoneRegion  = zoneRegion
    )
}
