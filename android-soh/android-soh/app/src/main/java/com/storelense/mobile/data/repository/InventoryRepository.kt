package com.storelense.mobile.data.repository

import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.CommissionTagRequest
import com.storelense.mobile.data.remote.dto.CommissionTagResponse
import com.storelense.mobile.data.remote.dto.IdentifyEpcDto
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun identifyEpc(epc: String, storeId: String): Result<IdentifyEpcDto?> = try {
        val resp = api.identifyEpc(epc.uppercase(), storeId)
        when {
            resp.code() == 404 -> Result.Success(null)
            resp.isSuccessful && resp.body()?.success == true -> Result.Success(resp.body()?.data)
            else -> Result.Error(resp.body()?.message ?: "Identify failed (HTTP ${resp.code()})")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun commissionTagItem(
        storeId: String,
        sku: String,
        epc: String,
        zoneCode: String
    ): Result<CommissionTagResponse> = try {
        val resp = api.commissionTagItem(CommissionTagRequest(storeId, sku, epc.uppercase(), zoneCode))
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Failed to register tag (HTTP ${resp.code()})")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }
}
