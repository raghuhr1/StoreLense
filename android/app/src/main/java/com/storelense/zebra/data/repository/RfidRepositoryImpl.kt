package com.storelense.zebra.data.repository

import com.storelense.zebra.data.local.dao.RfidReadDao
import com.storelense.zebra.data.local.entity.RfidReadEntity
import com.storelense.zebra.data.remote.ApiService
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.data.remote.dto.RfidReadBatchRequest
import com.storelense.zebra.data.remote.dto.RfidReadRequest
import com.storelense.zebra.data.remote.safeApiCall
import com.storelense.zebra.domain.model.RfidRead
import com.storelense.zebra.domain.repository.RfidRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RfidRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val dao: RfidReadDao,
) : RfidRepository {

    override suspend fun bufferRead(read: RfidRead) {
        dao.insertRead(RfidReadEntity(
            sessionId   = read.sessionId,
            epc         = read.epc,
            rssi        = read.rssi,
            antennaPort = read.antennaPort,
            readAt      = read.readAt,
        ))
    }

    override fun observeReadCount(sessionId: String): Flow<Int>   = dao.observeCount(sessionId)
    override fun observeUniqueCount(sessionId: String): Flow<Int> = dao.observeUniqueCount(sessionId)

    override suspend fun uploadPendingReads(sessionId: String, storeId: String, deviceId: String): NetworkResult<Int> {
        val pending = dao.getPendingUploads(sessionId)
        if (pending.isEmpty()) return NetworkResult.Success(0)

        val result = safeApiCall {
            api.ingestBatch(RfidReadBatchRequest(
                rfidSessionId = sessionId,
                storeId       = storeId,
                deviceId      = deviceId,
                readerId      = null,
                reads         = pending.map { r ->
                    RfidReadRequest(r.epc, r.rssi, r.antennaPort, r.readAt)
                },
            ))
        }

        if (result is NetworkResult.Success) {
            dao.markUploaded(sessionId, pending.map { it.epc })
            dao.pruneUploaded(sessionId)
        }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.published)
            is NetworkResult.Error   -> result
            is NetworkResult.Loading -> result
        }
    }
}
