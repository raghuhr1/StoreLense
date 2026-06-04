package com.storelense.zebra.data.repository

import com.storelense.zebra.data.local.dao.RefillTaskDao
import com.storelense.zebra.data.local.entity.RefillTaskEntity
import com.storelense.zebra.data.local.entity.RefillTaskItemEntity
import com.storelense.zebra.data.local.entity.RefillTaskWithItems
import com.storelense.zebra.data.remote.ApiService
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.data.remote.dto.RefillTaskDto
import com.storelense.zebra.data.remote.safeApiCall
import com.storelense.zebra.domain.model.RefillTask
import com.storelense.zebra.domain.model.RefillTaskItem
import com.storelense.zebra.domain.repository.RefillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefillRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val dao: RefillTaskDao,
) : RefillRepository {

    override fun observeTasks(storeId: String): Flow<List<RefillTask>> =
        dao.observeTasks(storeId).map { list -> list.map { it.toDomain() } }

    override fun observeTask(id: String): Flow<RefillTask?> =
        dao.observeTask(id).map { it?.toDomain() }

    override suspend fun syncTasks(storeId: String): NetworkResult<List<RefillTask>> =
        safeApiCall { api.listRefillTasks(storeId, size = 100) }
            .also { result ->
                if (result is NetworkResult.Success) {
                    result.data.content.forEach { dto ->
                        dao.upsertTaskWithItems(dto.toEntity(), dto.items.map { it.toItemEntity(dto.id) })
                    }
                }
            }
            .map { page -> page.content.map { it.toDomain() } }

    override suspend fun fulfilItem(taskId: String, itemId: String, quantity: Int): NetworkResult<RefillTask> {
        // Optimistic local update
        dao.setPendingFulfil(itemId, quantity)

        return safeApiCall { api.fulfilItem(taskId, itemId, quantity) }
            .also { result ->
                if (result is NetworkResult.Success) {
                    val updated = result.data.items.find { it.id == itemId }
                    if (updated != null) dao.confirmFulfil(itemId, updated.fulfilledQuantity, updated.status)
                } else {
                    dao.setPendingFulfil(itemId, -1)  // rollback
                }
            }
            .map { it.toDomain() }
    }
}

// ── Mappers ───────────────────────────────────────────────────────────────────

private fun RefillTaskDto.toEntity() = RefillTaskEntity(
    id, storeId, taskType, status, priority, source, dueDate, notes, createdBy, createdAt, completedAt,
)
private fun com.storelense.zebra.data.remote.dto.RefillTaskItemDto.toItemEntity(taskId: String) =
    RefillTaskItemEntity(id, taskId, productId, zoneId, requestedQuantity, fulfilledQuantity, status)

private fun RefillTaskDto.toDomain() = RefillTask(
    id, storeId, taskType, status, priority, source, dueDate, notes, createdAt,
    items.map { RefillTaskItem(it.id, id, it.productId, it.zoneId, it.requestedQuantity, it.fulfilledQuantity, it.status) }
)
private fun RefillTaskWithItems.toDomain() = RefillTask(
    task.id, task.storeId, task.taskType, task.status, task.priority, task.source,
    task.dueDate, task.notes, task.createdAt,
    items.map { RefillTaskItem(it.id, task.id, it.productId, it.zoneId, it.requestedQuantity, it.fulfilledQuantity, it.status) }
)
private fun <A, B> NetworkResult<A>.map(transform: (A) -> B): NetworkResult<B> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data))
    is NetworkResult.Error   -> this
    is NetworkResult.Loading -> this
}
