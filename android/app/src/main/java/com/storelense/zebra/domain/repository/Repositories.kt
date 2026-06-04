package com.storelense.zebra.domain.repository

import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): NetworkResult<User>
    suspend fun logout()
    fun currentUser(): User?
    fun isLoggedIn(): Boolean
}

interface SohRepository {
    fun observeSessions(storeId: String): Flow<List<SohSession>>
    suspend fun refreshSessions(storeId: String): NetworkResult<List<SohSession>>
    suspend fun createSession(storeId: String, zoneId: String?, type: String): NetworkResult<SohSession>
    suspend fun completeSession(id: String): NetworkResult<SohResult>
    suspend fun cancelSession(id: String): NetworkResult<Unit>
}

interface RfidRepository {
    suspend fun bufferRead(read: RfidRead)
    fun observeReadCount(sessionId: String): Flow<Int>
    fun observeUniqueCount(sessionId: String): Flow<Int>
    suspend fun uploadPendingReads(sessionId: String, storeId: String, deviceId: String): NetworkResult<Int>
}

interface RefillRepository {
    fun observeTasks(storeId: String): Flow<List<RefillTask>>
    fun observeTask(id: String): Flow<RefillTask?>
    suspend fun syncTasks(storeId: String): NetworkResult<List<RefillTask>>
    suspend fun fulfilItem(taskId: String, itemId: String, quantity: Int): NetworkResult<RefillTask>
}
