package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.EpcReadDao
import com.storelense.mobile.data.local.dao.SohSessionDao
import com.storelense.mobile.data.local.entity.EpcReadEntity
import com.storelense.mobile.data.local.entity.SohSessionEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.TokenManager
import com.storelense.mobile.data.remote.dto.*
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SohRepository @Inject constructor(
    private val api: ApiService,
    private val sessionDao: SohSessionDao,
    private val epcReadDao: EpcReadDao,
    private val tokenManager: TokenManager,
    @Named("deviceId") private val deviceId: String
) {
    fun sessionsFlow(storeId: String): Flow<List<SohSessionEntity>> =
        sessionDao.getForStore(storeId)

    suspend fun refreshSessions(storeId: String): Result<Unit> {
        return try {
            val resp = api.getSohSessions(storeId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true) {
                val sessions = body.data?.content ?: emptyList()
                sessionDao.deleteForStore(storeId)
                sessionDao.upsertAll(sessions.map { it.toEntity() })
                Result.Success(Unit)
            } else {
                Result.Error(body?.message ?: "Failed to load sessions")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getSession(id: String): Result<SohSessionDto> {
        return try {
            val resp = api.getSohSession(id, includeEpcs = false)
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
    }

    suspend fun getSessionEpcs(id: String): Result<List<String>> {
        return try {
            val resp = api.getSohSessionEpcs(id)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                Result.Success(body.data)
            } else {
                Result.Error(body?.message ?: "Failed to fetch EPCs")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getExpectedEpcs(id: String): Result<List<String>> {
        return try {
            val resp = api.getSohSessionExpectedEpcs(id)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                Result.Success(body.data)
            } else {
                Result.Error(body?.message ?: "Failed to fetch expected EPCs")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun createSession(storeId: String): Result<SohSessionDto> {
        return try {
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
    }

    // Creates a real zone-scoped session (locationCode set) instead of continuing on a
    // single Full Store session — this is what lets the zone picker on an ERP-triggered
    // task actually behave like the manual Sales Floor / Back Room flow: each zone gets
    // its own completable session, auto-grouped into the same day's cycle count, with
    // "Scan Another Zone" available afterward.
    //
    // source is deliberately "manual", NOT "erp_triggered": ScanViewModel shows the zone
    // picker again for whichever session it loads whenever isErpTriggered is true (source
    // == "erp_triggered") — if this new session carried that same source, picking a zone
    // on it would recursively create yet another session instead of ever actually
    // scanning. "manual" is safe here because this store's ERP import already exists
    // (that's why the Full Store task exists at all), so the ERP-import-required gate
    // that normally applies to manual sessions passes regardless.
    suspend fun createZoneSession(storeId: String, locationCode: String?): Result<SohSessionDto> {
        return try {
            val resp = api.createSohSession(CreateSohSessionRequest(
                storeId      = storeId,
                sessionType  = "manual",
                source       = "manual",
                locationCode = locationCode
            ))
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                sessionDao.upsert(body.data.toEntity())
                Result.Success(body.data)
            } else {
                Result.Error(body?.message ?: "Failed to create zone session")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun bufferEpc(
        sessionId: String,
        epc: String,
        rssi: Double?,
        antenna: Int?,
        zoneId: String? = null,
        locationCode: String? = null,
        sectionCode: String? = null
    ) {
        epcReadDao.insert(
            EpcReadEntity(
                sessionId    = sessionId,
                epc          = epc,
                rssi         = rssi,
                antennaPort  = antenna,
                scannedAt    = Instant.now().toString(),
                zoneId       = zoneId,
                locationCode = locationCode,
                sectionCode  = sectionCode
            )
        )
        Timber.d("bufferEpc: inserted epc=$epc sessionId=$sessionId")
    }

    fun epcCountFlow(sessionId: String): Flow<Int> = epcReadDao.countFlow(sessionId)

    suspend fun getPendingEpcs(sessionId: String): List<String> =
        epcReadDao.getPending(sessionId).map { it.epc }

    suspend fun getActiveDevices(sessionId: String): Int {
        val r = getParticipants(sessionId)
        return if (r is Result.Success) r.data.activeCount else 1
    }

    suspend fun joinSession(sessionId: String, deviceId: String, zoneRegion: String?): Result<ParticipantDto> {
        return try {
            val resp = api.joinSohSession(sessionId, JoinSessionRequest(deviceId, zoneRegion))
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                Result.Success(body.data)
            } else {
                Result.Error(body?.code ?: body?.message ?: "Failed to join session")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun markMyZoneDone(sessionId: String, deviceId: String): Result<MarkDoneDto> {
        return try {
            val resp = api.markParticipantDone(sessionId, deviceId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                Result.Success(body.data)
            } else {
                Result.Error(body?.message ?: "Failed to mark zone done")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getParticipants(sessionId: String): Result<ParticipantsListDto> {
        return try {
            val resp = api.getSohParticipants(sessionId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                Result.Success(body.data)
            } else {
                Result.Error(body?.message ?: "Failed to get participants")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun uploadBatch(sessionId: String, storeId: String): Result<Unit> {
        try {
            val pending = epcReadDao.getPending(sessionId)
            Timber.i("uploadBatch: sessionId=$sessionId pendingCount=${pending.size}")
            if (pending.isEmpty()) return Result.Success(Unit)
            val CHUNK = 300
            for (i in pending.indices step CHUNK) {
                val chunk = pending.subList(i, minOf(i + CHUNK, pending.size))
                val req = RfidBatchRequest(
                    rfidSessionId = sessionId,
                    storeId       = storeId,
                    deviceId      = deviceId,
                    reads         = chunk.map { RfidReadDto(it.epc, it.rssi, it.antennaPort, it.scannedAt, it.zoneId) }
                )
                val resp = api.ingestRfidBatch(req)
                Timber.i("uploadBatch: chunk $i..${i + chunk.size} -> HTTP ${resp.code()} successful=${resp.isSuccessful} body=${resp.errorBody()?.string()}")
                if (!resp.isSuccessful) return Result.Error("Upload failed at chunk $i")
            }
            epcReadDao.markAllUploaded(sessionId)
            Timber.i("uploadBatch: marked ${pending.size} reads uploaded for session $sessionId")
            return Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "uploadBatch: exception for session $sessionId")
            return Result.Error(e.message ?: "Upload error")
        }
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteById(sessionId)
    }

    suspend fun completeSession(sessionId: String): Result<SohSessionDto> {
        try {
            val resp = api.completeSohSession(sessionId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                return Result.Success(body.data)
            } else {
                return Result.Error(body?.message ?: "Complete failed")
            }
        } catch (e: Exception) {
            return Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun pauseSession(sessionId: String): Result<Unit> {
        return try {
            val resp = api.pauseSohSession(sessionId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true) Result.Success(Unit)
            else Result.Error(body?.message ?: "Pause failed")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun resumeSession(sessionId: String): Result<Unit> {
        return try {
            val resp = api.resumeSohSession(sessionId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true) Result.Success(Unit)
            else Result.Error(body?.message ?: "Resume failed")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun uploadSession(sessionId: String): Result<Unit> {
        return try {
            val resp = api.uploadSohSession(sessionId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true) Result.Success(Unit)
            else Result.Error(body?.message ?: "Upload failed")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    private fun SohSessionDto.toEntity() = SohSessionEntity(
        id            = id,
        storeId       = storeId,
        status        = status,
        sessionType   = sessionType,
        startedAt     = startedAt,
        source        = source,
        zoneRegion    = zoneRegion,
        locationCode  = locationCode,
        sectionCode   = sectionCode,
        cycleCountId  = cycleCountId,
        expectedCount = expectedEpcs?.size ?: result?.totalUnitsExpected ?: 0
    )
}
