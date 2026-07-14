package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.ProductDao
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.CommissionRequest
import com.storelense.mobile.data.remote.dto.CommissionResponseDto
import com.storelense.mobile.data.remote.dto.ProductDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService,
    private val productDao: ProductDao
) {
    fun productsFlow(storeId: String): Flow<List<ProductEntity>> =
        productDao.getAllForStore(storeId)

    suspend fun search(query: String, storeId: String): List<ProductEntity> {
        if (query.isBlank()) return emptyList()
        return productDao.search(query.trim(), storeId)
    }

    suspend fun getByEpc(epc: String): ProductEntity? = productDao.getByEpc(epc)

    suspend fun getById(id: String): ProductEntity? = productDao.getById(id)

    suspend fun syncProducts(storeId: String): Result<Int> = try {
        var page = 0
        var totalFetched = 0
        var hasMore = true
        val all = mutableListOf<ProductDto>()

        while (hasMore) {
            val resp = api.getProducts(storeId = storeId, page = page, size = 200)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                val paged = body.data
                all.addAll(paged.content)
                totalFetched += paged.content.size
                hasMore = page < paged.totalPages - 1
                page++
            } else {
                hasMore = false
            }
        }

        if (all.isNotEmpty()) {
            productDao.upsertAll(all.map { it.toEntity(storeId) })
        }
        Result.Success(totalFetched)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Sync error")
    }

    suspend fun catalogCount(storeId: String): Int = productDao.countForStore(storeId)

    suspend fun commissionTag(
        storeId: String,
        sku: String,
        epc: String,
        zone: String,
        replacesEpc: String? = null
    ): Result<CommissionResponseDto> = try {
        val resp = api.commissionTag(CommissionRequest(storeId, sku, epc, zone, replacesEpc))
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Failed to tag item")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    private fun ProductDto.toEntity(storeId: String) = ProductEntity(
        id           = id,
        sku          = sku,
        name         = name,
        description  = description,
        brand        = brand,
        category     = category,
        erpCode      = erpCode,
        storeId      = this.storeId ?: storeId,
        onHandQty    = onHandQty,
        expectedQty  = expectedQty,
        imageUrl     = imageUrl
    )
}
