package com.storelense.zebra.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    // Use Provider to avoid circular DI — ApiService depends on this interceptor
    private val apiServiceProvider: Provider<ApiService>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else chain.request()

        val response = chain.proceed(request)

        // Auto-refresh on 401
        if (response.code == 401) {
            response.close()
            val refreshToken = tokenManager.getRefreshToken() ?: return response
            Timber.d("Access token expired, attempting refresh")

            val refreshed = runBlocking {
                runCatching {
                    apiServiceProvider.get().refresh(
                        com.storelense.zebra.data.remote.dto.RefreshRequest(refreshToken)
                    )
                }.getOrNull()
            }

            return if (refreshed?.success == true && refreshed.data != null) {
                tokenManager.setTokens(refreshed.data.accessToken, refreshed.data.refreshToken)
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer ${refreshed.data.accessToken}")
                        .build()
                )
            } else {
                tokenManager.clearTokens()
                response
            }
        }

        return response
    }
}
