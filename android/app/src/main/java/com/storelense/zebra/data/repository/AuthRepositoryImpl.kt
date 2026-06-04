package com.storelense.zebra.data.repository

import com.storelense.zebra.data.remote.ApiService
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.data.remote.TokenManager
import com.storelense.zebra.data.remote.dto.LoginRequest
import com.storelense.zebra.data.remote.dto.RefreshRequest
import com.storelense.zebra.data.remote.safeApiCall
import com.storelense.zebra.domain.model.User
import com.storelense.zebra.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api:          ApiService,
    private val tokenManager: TokenManager,
) : AuthRepository {

    override suspend fun login(username: String, password: String): NetworkResult<User> =
        safeApiCall { api.login(LoginRequest(username, password)) }
            .also { result ->
                if (result is NetworkResult.Success) {
                    val d = result.data
                    tokenManager.setTokens(d.accessToken, d.refreshToken)
                    tokenManager.saveUser(d.userId, d.username, d.role, d.storeId)
                }
            }
            .map { d -> User(d.userId, d.username, d.role, d.storeId) }

    override suspend fun logout() {
        val rt = tokenManager.getRefreshToken()
        if (rt != null) runCatching { api.logout(RefreshRequest(rt)) }
        tokenManager.clearTokens()
    }

    override fun currentUser(): User? {
        val id       = tokenManager.getUserId()  ?: return null
        val username = tokenManager.getUsername() ?: return null
        val role     = tokenManager.getRole()     ?: return null
        return User(id, username, role, tokenManager.getStoreId())
    }

    override fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
}

private fun <A, B> NetworkResult<A>.map(transform: (A) -> B): NetworkResult<B> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data))
    is NetworkResult.Error   -> this
    is NetworkResult.Loading -> this
}
