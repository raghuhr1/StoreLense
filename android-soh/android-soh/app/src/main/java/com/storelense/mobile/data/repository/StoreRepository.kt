package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.StoreDao
import com.storelense.mobile.data.local.entity.StoreEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.ZoneDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepository @Inject constructor(
    private val api: ApiService,
    private val storeDao: StoreDao
) {
    fun storesFlow(): Flow<List<StoreEntity>> = storeDao.getAll()

    suspend fun getStoresSync(): List<StoreEntity> = storeDao.getAllSync()

    suspend fun refreshStores(): Result<Int> = try {
        val resp = api.getStores()
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            val stores = body.data.content.map { dto ->
                StoreEntity(id = dto.id, name = dto.name, code = dto.code)
            }
            storeDao.deleteAll()
            storeDao.upsertAll(stores)
            Result.Success(stores.size)
        } else {
            Result.Error(body?.message ?: "Failed to load stores")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun count(): Int = storeDao.count()

    suspend fun getZones(storeId: String): Result<List<ZoneDto>> = try {
        val resp = api.getZones(storeId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            Result.Success(body.data.filter { it.active }.sortedBy { it.displayOrder })
        } else {
            Result.Error(body?.message ?: "Failed to load zones")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }
}
