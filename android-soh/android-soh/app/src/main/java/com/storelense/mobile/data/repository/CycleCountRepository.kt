package com.storelense.mobile.data.repository

import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CycleCountRepository @Inject constructor(
    private val api: ApiService
) {

    suspend fun list(storeId: String, page: Int = 0): Result<PagedData<CycleCountDto>> {
        return try {
            val resp = api.getCycleCounts(storeId, page)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to load cycle counts")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun get(id: String): Result<CycleCountDto> {
        return try {
            val resp = api.getCycleCount(id)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Cycle count not found")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun create(storeId: String, notes: String? = null): Result<CycleCountDto> {
        return try {
            val resp = api.createCycleCount(CreateCycleCountRequest(storeId = storeId, notes = notes))
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to create cycle count")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun upload(id: String): Result<CycleCountDto> {
        return try {
            val resp = api.uploadCycleCount(id)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Upload failed")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun close(id: String): Result<CycleCountDto> {
        return try {
            val resp = api.closeCycleCount(id)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Close failed")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getStoreLocations(storeId: String): Result<List<StoreLocationDto>> {
        return try {
            val resp = api.getStoreLocations(storeId)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to load store locations")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun startSession(
        storeId: String,
        cycleCountId: String,
        locationCode: String,
        sectionCode: String?
    ): Result<SohSessionDto> {
        return try {
            val resp = api.createSohSession(
                CreateSohSessionRequest(
                    storeId      = storeId,
                    sessionType  = "cycle_count",
                    source       = "manual",
                    cycleCountId = cycleCountId,
                    locationCode = locationCode,
                    sectionCode  = sectionCode
                )
            )
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null)
                Result.Success(body.data)
            else
                Result.Error(body?.message ?: "Failed to start session")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
