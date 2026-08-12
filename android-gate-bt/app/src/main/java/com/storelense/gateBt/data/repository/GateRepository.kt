package com.storelense.gateBt.data.repository

import com.storelense.gateBt.data.remote.ApiService
import com.storelense.gateBt.data.remote.TokenManager
import com.storelense.gateBt.data.remote.dto.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GateRepository @Inject constructor(
    private val api:          ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun resolveEan(ean: String): Result<EpcsByEanResponse> {
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.getEpcsByEan(ean, storeId)
            val body = resp.body()
            if (resp.isSuccessful && (body?.success == true) && (body.data != null))
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "EAN not found")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** Look up an unknown EPC to try to resolve it to a product. */
    suspend fun identifyEpc(epc: String): Result<IdentifyEpcResponse?> {
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.identifyEpc(epc, storeId)
            when {
                resp.code() == 404 -> Result.Success(null)
                resp.isSuccessful && resp.body()?.success == true -> Result.Success(resp.body()?.data)
                else -> Result.Error(resp.body()?.message ?: "Lookup failed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun markSold(epcs: List<String>): Result<Int> {
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.markEpcsSold(MarkEpcsSoldRequest(storeId, epcs))
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true)
                Result.Success(body.data?.marked ?: epcs.size)
            else
                Result.Error(body?.message ?: "Failed to mark sold")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun markNonRfidSold(items: Map<String, Int>): Result<Map<String, Int>> {
        if (items.isEmpty()) return Result.Success(emptyMap())
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.markNonRfidSold(
                MarkNonRfidSoldRequest(storeId, items.map { (ean, qty) -> NonRfidSaleItem(ean, qty) })
            )
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true)
                Result.Success(body.data ?: emptyMap())
            else
                Result.Error(body?.message ?: "Failed to mark non-RFID items sold")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun lookupBill(billRef: String): Result<BillLookupResponse> {
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.lookupBill(billRef, storeId)
            val body = resp.body()
            when {
                resp.isSuccessful && body?.success == true && body.data != null -> Result.Success(body.data)
                resp.code() == 404 -> Result.Error("Bill '$billRef' not found — ask cashier to re-scan")
                else -> Result.Error(body?.message ?: "Bill lookup failed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun recordGateCheck(
        billRef:       String,
        expectedCount: Int,
        matchedCount:  Int,
        extraCount:    Int,
        outcome:       String,
        epcsMatched:   List<String>,
        epcsExtra:     List<String>
    ): Result<Unit> {
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.recordGateCheck(
                GateCheckRequest(storeId, billRef, expectedCount, matchedCount, extraCount, outcome, epcsMatched, epcsExtra)
            )
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error("Failed to record gate check")
        } catch (e: Exception) {
            Timber.e(e, "Error recording gate check (ignored)")
            Result.Success(Unit) // fire-and-forget — don't block release on logging failure
        }
    }

    suspend fun getMySummary(): Result<GateCheckSummaryDto> {
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.getMyGateCheckSummary(storeId)
            val body = resp.body()
            if (resp.isSuccessful && (body?.success == true) && (body.data != null))
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to load summary")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getMyRecentChecks(): Result<List<GateCheckDto>> {
        val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
        return try {
            val resp = api.getMyRecentGateChecks(storeId)
            val body = resp.body()
            if (resp.isSuccessful && (body?.success == true) && (body.data != null))
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to load recent checks")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
