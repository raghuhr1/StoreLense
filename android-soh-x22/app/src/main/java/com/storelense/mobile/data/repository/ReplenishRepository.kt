package com.storelense.mobile.data.repository

import com.storelense.mobile.data.local.dao.RefillDao
import com.storelense.mobile.data.local.entity.RefillTaskEntity
import com.storelense.mobile.data.local.entity.RefillTaskItemEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.FulfilItemRequest
import com.storelense.mobile.data.remote.dto.RefillTaskDto
import com.storelense.mobile.data.remote.dto.RefillTaskItemDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplenishRepository @Inject constructor(
    private val api: ApiService,
    private val dao: RefillDao
) {
    fun tasksFlow(storeId: String): Flow<List<RefillTaskEntity>> =
        dao.getTasksForStore(storeId)

    fun itemsFlow(taskId: String): Flow<List<RefillTaskItemEntity>> =
        dao.getItemsForTask(taskId)

    fun allItemsFlow(storeId: String): Flow<List<RefillTaskItemEntity>> =
        dao.getAllItemsForStore(storeId)

    suspend fun refreshTasks(storeId: String): Result<Unit> = try {
        val resp = api.getRefillTasks(storeId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            val tasks = body.data?.content ?: emptyList()
            dao.upsertTasks(tasks.map { it.toTaskEntity() })
            tasks.forEach { task ->
                task.items?.let { items ->
                    dao.upsertItems(items.map { it.toItemEntity() })
                }
            }
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Failed to load tasks")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getTask(taskId: String): Result<RefillTaskDto> = try {
        val resp = api.getRefillTask(taskId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && body.data != null) {
            dao.upsertTasks(listOf(body.data.toTaskEntity()))
            body.data.items?.let { dao.upsertItems(it.map { item -> item.toItemEntity() }) }
            Result.Success(body.data)
        } else {
            Result.Error(body?.message ?: "Task not found")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun fulfilItem(
        taskId: String,
        itemId: String,
        qty: Int,
        scannedEpcs: List<String> = emptyList()
    ): Result<Unit> {
        dao.setPendingQty(itemId, qty)
        return try {
            val resp = api.fulfilItem(taskId, itemId, FulfilItemRequest(qty, scannedEpcs.ifEmpty { null }))
            val body = resp.body()
            if (resp.isSuccessful && body?.success == true) {
                dao.confirmFulfil(itemId, qty)
                Result.Success(Unit)
            } else {
                dao.setPendingQty(itemId, -1)
                Result.Error(body?.message ?: "Fulfil failed")
            }
        } catch (e: Exception) {
            dao.setPendingQty(itemId, -1)
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun completeTask(taskId: String): Result<Unit> = try {
        val resp = api.completeRefillTask(taskId)
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            dao.markCompleted(taskId)
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Complete failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    private fun RefillTaskDto.toTaskEntity() = RefillTaskEntity(
        id        = id,
        storeId   = storeId,
        status    = status,
        priority  = priority,
        dueBy     = dueBy,
        itemCount = itemCount
    )

    private fun RefillTaskItemDto.toItemEntity() = RefillTaskItemEntity(
        id           = id,
        taskId       = taskId,
        sku          = sku,
        productName  = productName,
        fromZone     = fromZone,
        toZone       = toZone,
        requiredQty  = requiredQty,
        fulfilledQty = fulfilledQty
    )
}
