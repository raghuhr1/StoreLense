package com.storelense.c66.data.repository

import com.storelense.c66.data.remote.ApiService
import com.storelense.c66.data.remote.TokenManager
import com.storelense.c66.data.remote.dto.EpcsByEanResponse
import com.storelense.c66.data.remote.dto.MarkEpcsSoldRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GateRepository @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {
    val storeId: String? get() = tokenManager.storeId

    /**
     * For a given EAN (from the customer's bill), fetches every EPC
     * currently in_store at this store so the gate can match against the RFID bag scan.
     */
    suspend fun resolveEan(ean: String): Result<EpcsByEanResponse> {
        val storeId = tokenManager.storeId ?: return Result.Error("No store assigned")
        return try {
            val resp = api.getEpcsByEan(ean, storeId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                Result.Success(body.data)
            } else {
                Result.Error(body?.message ?: "EAN $ean not found in store")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** Marks the matched EPCs as sold in the RFID ledger. */
    suspend fun markSold(epcs: List<String>): Result<Int> {
        val storeId = tokenManager.storeId ?: return Result.Error("No store assigned")
        return try {
            val resp = api.markEpcsSold(MarkEpcsSoldRequest(storeId, epcs))
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true) {
                Result.Success(body.data?.marked ?: epcs.size)
            } else {
                Result.Error(body?.message ?: "Failed to mark EPCs sold")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
