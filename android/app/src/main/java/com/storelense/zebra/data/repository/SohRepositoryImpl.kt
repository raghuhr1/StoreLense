package com.storelense.zebra.data.repository

import com.storelense.zebra.data.local.dao.SohSessionDao
import com.storelense.zebra.data.local.entity.SohSessionEntity
import com.storelense.zebra.data.remote.ApiService
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.data.remote.dto.CreateSohSessionRequest
import com.storelense.zebra.data.remote.safeApiCall
import com.storelense.zebra.domain.model.SohResult
import com.storelense.zebra.domain.model.SohSession
import com.storelense.zebra.domain.repository.SohRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SohRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val dao: SohSessionDao,
) : SohRepository {

    override fun observeSessions(storeId: String): Flow<List<SohSession>> =
        dao.observeSessions(storeId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshSessions(storeId: String): NetworkResult<List<SohSession>> =
        safeApiCall { api.listSessions(storeId, size = 50) }
            .also { result ->
                if (result is NetworkResult.Success) {
                    dao.upsertAll(result.data.content.map { it.toEntity() })
                }
            }
            .map { page -> page.content.map { it.toDomain() } }

    override suspend fun createSession(storeId: String, zoneId: String?, type: String): NetworkResult<SohSession> =
        safeApiCall { api.createSession(CreateSohSessionRequest(storeId, zoneId, type)) }
            .also { result ->
                if (result is NetworkResult.Success) dao.upsert(result.data.toEntity())
            }
            .map { it.toDomain() }

    override suspend fun completeSession(id: String): NetworkResult<SohResult> =
        safeApiCall { api.completeSession(id) }
            .also {
                if (it is NetworkResult.Success)
                    dao.updateStatus(id, "completed", it.data.resultGeneratedAt)
            }
            .map { SohResult(it.accuracyPct, it.varianceCount, it.totalUnitsCounted, it.totalUnitsExpected) }

    override suspend fun cancelSession(id: String): NetworkResult<Unit> =
        safeApiCall { api.cancelSession(id) }
            .also {
                if (it is NetworkResult.Success)
                    dao.updateStatus(id, "cancelled", null)
            }
            .map { Unit }
}

// ── Mappers ───────────────────────────────────────────────────────────────────

private fun com.storelense.zebra.data.remote.dto.SohSessionDto.toEntity() = SohSessionEntity(
    id, storeId, zoneId, sessionType, status, startedBy, startedAt, completedAt, totalEpcReads, uniqueEpcCount, notes,
)
private fun com.storelense.zebra.data.remote.dto.SohSessionDto.toDomain() = SohSession(
    id, storeId, zoneId, sessionType, status, startedAt, completedAt, totalEpcReads, uniqueEpcCount, notes,
)
private fun SohSessionEntity.toDomain() = SohSession(
    id, storeId, zoneId, sessionType, status, startedAt, completedAt, totalEpcReads, uniqueEpcCount, notes,
)
private fun <A, B> NetworkResult<A>.map(transform: (A) -> B): NetworkResult<B> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data))
    is NetworkResult.Error   -> this
    is NetworkResult.Loading -> this
}
