package com.storelense.c66.data.repository

import com.storelense.c66.data.remote.ApiService
import com.storelense.c66.data.remote.TokenManager
import com.storelense.c66.data.remote.dto.BillLookupResponse
import com.storelense.c66.data.remote.dto.GateCheckRequest
import com.storelense.c66.data.remote.dto.GateCheckDto
import com.storelense.c66.data.remote.dto.GateCheckSummaryDto
import com.storelense.c66.data.remote.dto.EpcsByEanResponse
import com.storelense.c66.data.remote.dto.MarkEpcsSoldRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GateRepository @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun resolveEan(ean: String): Result<EpcsByEanResponse> {
        return try {
            val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
            val resp = api.getEpcsByEan(ean, storeId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "EAN not found")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun markSold(epcs: List<String>): Result<Int> {
        return try {
            val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
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

    suspend fun lookupBill(billRef: String): Result<BillLookupResponse> {
        return try {
            val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
            val resp = api.lookupBill(billRef, storeId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else if (resp.code() == 404)
                Result.Error("Bill '$billRef' not found — ask cashier to re-scan")
            else
                Result.Error(body?.message ?: "Bill lookup failed")
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
        return try {
            val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
            val req = GateCheckRequest(
                storeId       = storeId,
                billRef       = billRef,
                expectedCount = expectedCount,
                matchedCount  = matchedCount,
                extraCount    = extraCount,
                outcome       = outcome,
                epcsMatched   = epcsMatched,
                epcsExtra     = epcsExtra
            )
            val resp = api.recordGateCheck(req)
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error("Failed to record gate check")
        } catch (e: Exception) {
            Result.Success(Unit) // fire-and-forget: don't block release on logging failure
        }
    }

    suspend fun getMySummary(): Result<GateCheckSummaryDto> {
        return try {
            val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
            val resp = api.getMyGateCheckSummary(storeId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to load summary")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getMyRecentChecks(): Result<List<GateCheckDto>> {
        return try {
            val storeId = tokenManager.storeId ?: return Result.Error("Not logged in")
            val resp = api.getMyRecentGateChecks(storeId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to load recent checks")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
