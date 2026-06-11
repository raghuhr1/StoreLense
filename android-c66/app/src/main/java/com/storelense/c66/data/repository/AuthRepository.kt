package com.storelense.c66.data.repository

import com.storelense.c66.data.remote.ApiService
import com.storelense.c66.data.remote.TokenManager
import com.storelense.c66.data.remote.dto.LoginRequest
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(username: String, password: String): Result<Unit> = try {
        val resp = api.login(LoginRequest(username, password))
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true) {
            val data = body.data!!
            tokenManager.accessToken  = data.accessToken
            tokenManager.refreshToken = data.refreshToken
            tokenManager.username     = data.username
            tokenManager.storeId      = data.storeId
            Result.Success(Unit)
        } else {
            Result.Error(body?.message ?: "Login failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    fun logout() = tokenManager.clear()

    val isLoggedIn get() = tokenManager.isLoggedIn
    val username   get() = tokenManager.username
    val storeId    get() = tokenManager.storeId
}
