package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.TransferDao
import com.storelense.mobile.data.local.entity.TransferManifestEntity
import com.storelense.mobile.data.local.entity.TransferOutEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.CreateTransferRequest
import com.storelense.mobile.data.remote.dto.ReceiveTransferRequest
import com.storelense.mobile.data.remote.dto.TransferDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val api: ApiService,
    private val transferDao: TransferDao
) {
    fun transfersFlow(sourceStoreId: String): Flow<List<TransferOutEntity>> =
        transferDao.getForStore(sourceStoreId)

    suspend fun createTransfer(
        sourceStoreId: String,
        destStoreId: String,
        transferType: String,
        epcs: List<String>
    ): Result<TransferDto> = try {
        val localId = UUID.randomUUID().toString()

        // Buffer offline before network call so no scan data is lost
        transferDao.insert(
            TransferOutEntity(
                id            = localId,
                sourceStoreId = sourceStoreId,
                destStoreId   = destStoreId,
                transferType  = transferType,
                epcsText      = epcs.joinToString("|"),
                status        = "PENDING",
                createdAt     = System.currentTimeMillis()
            )
        )

        val resp = api.createTransfer(
            CreateTransferRequest(sourceStoreId, destStoreId, transferType, epcs)
        )
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            transferDao.updateStatus(localId, "SUBMITTED", System.currentTimeMillis())
            Result.Success(body.data)
        } else {
            transferDao.updateStatus(localId, "FAILED")
            Result.Error(body?.message ?: "Transfer creation failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getManifest(transferId: String): Result<List<String>> = try {
        val resp = api.getTransfer(transferId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            val epcs = body.data.epcs
            // Cache manifest rows so Transfer Receive can work offline
            transferDao.insertManifestItems(
                epcs.map { TransferManifestEntity(transferId = transferId, epc = it) }
            )
            Result.Success(epcs)
        } else {
            Result.Error(body?.message ?: "Transfer not found")
        }
    } catch (e: Exception) {
        // Fall back to Room cache if network fails
        val cached = transferDao.getManifest(transferId).map { it.epc }
        if (cached.isNotEmpty()) Result.Success(cached)
        else Result.Error(e.message ?: "No cached manifest available")
    }

    suspend fun receiveTransfer(
        transferId: String,
        receivedEpcs: List<String>
    ): Result<Unit> = try {
        val resp = api.receiveTransfer(transferId, ReceiveTransferRequest(receivedEpcs))
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            val now = System.currentTimeMillis()
            receivedEpcs.forEach { epc ->
                transferDao.markEpcReceived(transferId, epc, now)
            }
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Receive failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    fun epcsFromText(text: String): List<String> =
        if (text.isBlank()) emptyList() else text.split("|")
}
