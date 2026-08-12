package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.ExceptionCacheDao
import com.storelense.mobile.data.local.dao.GhostAnalysisDao
import com.storelense.mobile.data.local.entity.ExceptionCacheEntity
import com.storelense.mobile.data.local.entity.GhostAnalysisEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExceptionRepository @Inject constructor(
    private val api: ApiService,
    private val exceptionCacheDao: ExceptionCacheDao,
    private val ghostAnalysisDao: GhostAnalysisDao
) {
    fun exceptionsByTypeFlow(type: String, storeId: String): Flow<List<ExceptionCacheEntity>> =
        exceptionCacheDao.getByType(type, storeId)

    suspend fun getSummary(storeId: String): Result<ExceptionSummaryDto> = try {
        val resp = api.getExceptionsSummary(storeId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Failed to load summary")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun listByType(
        storeId: String,
        type: String,
        page: Int = 0
    ): Result<List<ExceptionItemDto>> = try {
        val resp = api.getExceptions(storeId, type, page)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            val items = body.data.content
            exceptionCacheDao.upsertAll(items.map { it.toEntity(storeId) })
            Result.Success(items)
        } else {
            Result.Error(body?.message ?: "Failed to load exceptions")
        }
    } catch (e: Exception) {
        // Serve cached rows when offline
        val cached = exceptionCacheDao.getByTypeSync(type, storeId).map { it.toDto() }
        if (cached.isNotEmpty()) Result.Success(cached)
        else Result.Error(e.message ?: "No cached exceptions available")
    }

    suspend fun getGhostDetail(epc: String): Result<GhostAnalysisDetailDto> = try {
        val resp = api.getGhostDetail(epc)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            val detail = body.data
            ghostAnalysisDao.upsert(
                GhostAnalysisEntity(
                    epc             = detail.epc,
                    confidenceScore = detail.confidenceScore,
                    reasonsText     = detail.reasons.joinToString("|"),
                    status          = detail.status
                )
            )
            Result.Success(detail)
        } else {
            Result.Error(body?.message ?: "Ghost detail not found")
        }
    } catch (e: Exception) {
        // Fall back to cached ghost detail
        val cached = ghostAnalysisDao.getByEpc(epc)
        if (cached != null) {
            Result.Success(
                GhostAnalysisDetailDto(
                    epc             = cached.epc,
                    status          = cached.status,
                    confidenceScore = cached.confidenceScore,
                    reasons         = cached.reasonsText.split("|").filter { it.isNotBlank() },
                    firstSeen       = null,
                    lastSeen        = null
                )
            )
        } else {
            Result.Error(e.message ?: "Network error and no cache available")
        }
    }

    suspend fun getMissingDetail(epc: String): Result<MissingEpcDetailDto> = try {
        val resp = api.getMissingDetail(epc)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Missing EPC detail not found")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun ignoreGhost(epc: String): Result<Unit> = try {
        val resp = api.ignoreGhost(epc)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            exceptionCacheDao.updateStatus(epc, "IGNORED")
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Action failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun investigateGhost(epc: String): Result<Unit> = try {
        val resp = api.investigateGhost(epc)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            exceptionCacheDao.updateStatus(epc, "INVESTIGATING")
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Action failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun markMissing(epc: String): Result<Unit> = try {
        val resp = api.markMissing(epc)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            exceptionCacheDao.updateStatus(epc, "RESOLVED")
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Action failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun ExceptionItemDto.toEntity(storeId: String) = ExceptionCacheEntity(
        epc            = epc,
        storeId        = storeId,
        type           = type,
        confidence     = confidence,
        classification = classification,
        lastSeen       = lastSeen,
        status         = status
    )

    private fun ExceptionCacheEntity.toDto() = ExceptionItemDto(
        epc            = epc,
        type           = type,
        confidence     = confidence,
        classification = classification,
        lastSeen       = lastSeen,
        status         = status
    )
}
