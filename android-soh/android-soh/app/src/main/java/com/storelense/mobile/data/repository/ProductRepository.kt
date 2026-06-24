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

    suspend fun getByEpc(epc: String, storeId: String): ProductEntity? = productDao.getByEpc(epc, storeId)

    suspend fun getById(id: String): ProductEntity? = productDao.getById(id)

    suspend fun syncProducts(storeId: String, forceFull: Boolean = false): Result<Int> {
        return try {
            // Step 1: get store-specific inventory quantities.
            val invResp = try {
                api.getInventoryState(storeId)
            } catch (e: Exception) {
                return Result.Error("Inventory API failed: ${e.message}")
            }

            val invByProductId: Map<String, Pair<Int, Int>> =
                if (invResp.isSuccessful && invResp.body()?.success == true) {
                    val rows = invResp.body()?.data ?: emptyList()
                    rows.groupBy { it.productId }
                        .mapValues { (_, zoneRows) ->
                            Pair(
                                zoneRows.sumOf { it.quantityOnHand },
                                zoneRows.sumOf { it.quantityExpected }
                            )
                        }
                } else {
                    return Result.Error("Inventory API error: ${invResp.code()} ${invResp.message()}")
                }

            // Step 2: Determine if we should do a delta sync (only new/updated since last time)
            val lastSync = if (forceFull) null else productDao.getMaxLastSynced(storeId)

            // Step 3: fetch products from server.
            // sync=false lets the server handle the storeId filtering to prevent global catalog issues.
            var page = 0
            var hasMore = true
            val incoming = mutableListOf<ProductDto>()
            while (hasMore) {
                val resp = try {
                    api.getProducts(
                        storeId = storeId, 
                        page    = page, 
                        size    = 200, 
                        sync    = false, 
                        since   = lastSync
                    )
                } catch (e: Exception) {
                    return Result.Error("Products API failed at page $page: ${e.message}")
                }

                val body = resp.body()
                if (resp.isSuccessful && body?.success == true && body.data != null) {
                    incoming.addAll(body.data.content)
                    hasMore = page < body.data.totalPages - 1
                    page++
                } else {
                    return Result.Error("Products API error: ${resp.code()} ${body?.message}")
                }
            }

            // Step 4: Map all server-returned products to entities. The server already
            // store-scoped the results, so no further filtering is needed. Supplement with
            // inventory quantities where available (products visible only via epc_registry
            // will have zero inventory counts, which is correct).
            val storeProducts = incoming.map { dto ->
                val (onHand, expected) = invByProductId[dto.id] ?: Pair(0, 0)
                dto.toEntity(storeId, onHand, expected)
            }

            // Step 5: Update database
            if (storeProducts.isNotEmpty()) {
                if (forceFull || lastSync == null) {
                    productDao.deleteForStore(storeId)
                }
                productDao.upsertAll(storeProducts)
            }
            Result.Success(storeProducts.size)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Sync error")
        }
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
