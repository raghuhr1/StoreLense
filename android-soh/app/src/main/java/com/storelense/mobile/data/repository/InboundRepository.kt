package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.InboundReadDao
import com.storelense.mobile.data.local.dao.InboundShipmentDao
import com.storelense.mobile.data.local.entity.InboundReadEntity
import com.storelense.mobile.data.local.entity.InboundShipmentEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.InboundReceiveRequest
import com.storelense.mobile.data.remote.dto.InboundResultDto
import com.storelense.mobile.data.remote.dto.InboundShipmentDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InboundRepository @Inject constructor(
    private val api: ApiService,
    private val shipmentDao: InboundShipmentDao,
    private val readDao: InboundReadDao
) {
    fun shipmentsFlow(storeId: String): Flow<List<InboundShipmentEntity>> =
        shipmentDao.getForStore(storeId)

    suspend fun refreshShipments(storeId: String): Result<Unit> = try {
        val resp = api.getShipments(storeId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            val items = body.data?.content ?: emptyList()
            shipmentDao.upsertAll(items.map { it.toEntity() })
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Failed to load shipments")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getShipment(id: String): Result<InboundShipmentDto> = try {
        val resp = api.getShipment(id)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Shipment not found")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun bufferEpc(shipmentId: String, epc: String) {
        readDao.insert(
            InboundReadEntity(
                shipmentId = shipmentId,
                epc        = epc,
                scannedAt  = Instant.now().toString()
            )
        )
    }

    fun epcCountFlow(shipmentId: String): Flow<Int> = readDao.countFlow(shipmentId)

    suspend fun receiveShipment(shipmentId: String): Result<InboundResultDto> = try {
        val pending = readDao.getPending(shipmentId)
        val resp = api.receiveShipment(
            shipmentId,
            InboundReceiveRequest(pending.map { it.epc })
        )
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            readDao.markAllUploaded(shipmentId)
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Receive failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    private fun InboundShipmentDto.toEntity() = InboundShipmentEntity(
        id              = id,
        storeId         = storeId,
        dcCode          = dcCode,
        referenceNumber = referenceNumber,
        status          = status,
        expectedAt      = expectedAt,
        lineCount       = lineCount
    )
}
