package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.ProductDao
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.ApiService
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
        // Step 1: get store-specific inventory quantities and the set of relevant productIds.
        // inventory/state is populated by ERP imports (quantityExpected) and RFID scans
        // (quantityOnHand), so it correctly reflects what is actually in this store.
        val invResp = api.getInventoryState(storeId)
        val invByProductId: Map<String, Pair<Int, Int>> =
            if (invResp.isSuccessful && invResp.body()?.success == true) {
                val rows = invResp.body()?.data ?: emptyList()
                // Aggregate across zones: sum quantities per product
                rows.groupBy { it.productId }
                    .mapValues { (_, zoneRows) ->
                        Pair(
                            zoneRows.sumOf { it.quantityOnHand },
                            zoneRows.sumOf { it.quantityExpected }
                        )
                    }
            } else emptyMap()

        // Step 2: fetch global product catalog (backend ignores storeId, returns all products)
        var page = 0
        var hasMore = true
        val all = mutableListOf<ProductDto>()
        while (hasMore) {
            val resp = api.getProducts(storeId = storeId, page = page, size = 200)
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true && body.data != null) {
                all.addAll(body.data.content)
                hasMore = page < body.data.totalPages - 1
                page++
            } else hasMore = false
        }

        // Step 3: keep only products that have an inventory record for this store.
        // If inventory state is empty (store has no data yet), fall back to the full catalog
        // so the app isn't blank on first setup.
        val storeProducts = if (invByProductId.isNotEmpty()) {
            all.filter { it.id in invByProductId }.map { dto ->
                val (onHand, expected) = invByProductId[dto.id] ?: Pair(0, 0)
                dto.toEntity(storeId, onHand, expected)
            }
        } else {
            all.map { it.toEntity(storeId, 0, 0) }
        }

        productDao.deleteForStore(storeId)
        productDao.upsertAll(storeProducts)
        Result.Success(storeProducts.size)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Sync error")
    }

    suspend fun catalogCount(storeId: String): Int = productDao.countForStore(storeId)

    private fun ProductDto.toEntity(storeId: String, onHandQty: Int, expectedQty: Int) = ProductEntity(
        id           = id,
        sku          = sku,
        name         = name,
        description  = description,
        brand        = brand,
        category     = category,
        erpCode      = erpCode,
        storeId      = storeId,
        onHandQty    = onHandQty,
        expectedQty  = expectedQty,
        imageUrl     = imageUrl
    )
}
