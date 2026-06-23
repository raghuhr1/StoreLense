package com.storelense.mobile.data.remote

import com.google.gson.Gson
import com.storelense.mobile.data.remote.dto.ApiResponse
import com.storelense.mobile.data.remote.dto.LoginData
import com.storelense.mobile.data.remote.dto.RefreshRequest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val gson: Gson
) : Interceptor {

    private val lock = Any()
    var onTokenExpired: (() -> Unit)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().withBearer(tokenManager.accessToken)
        val resp = chain.proceed(req)

        // 401 = explicit Unauthorized; 403 can mean expired JWT fell through to anonymous user
        if (resp.code != 401 && resp.code != 403) return resp
        resp.close()

        val newToken = synchronized(lock) {
            val current = tokenManager.accessToken
            val freshToken = tokenManager.accessToken
            if (current != freshToken) {
                freshToken
            } else {
                tryRefresh(chain)
            }
        } ?: run {
            onTokenExpired?.invoke()
            return chain.proceed(req.withBearer(null))
        }

        return chain.proceed(req.withBearer(newToken))
    }

    private fun tryRefresh(chain: Interceptor.Chain): String? {
        val refresh = tokenManager.refreshToken ?: return null
        return try {
            val body = gson.toJson(RefreshRequest(refresh))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(chain.request().url.newBuilder().encodedPath("/api/auth/refresh").build())
                .post(body).build()
            val resp = chain.proceed(req)
            val raw = resp.body?.string()
            resp.close()
            if (!resp.isSuccessful || raw == null) { onTokenExpired?.invoke(); return null }
            val parsed = gson.fromJson(raw, ApiResponse::class.java)
            @Suppress("UNCHECKED_CAST")
            val data = (parsed.data as? Map<*, *>) ?: return null
            val newAccess = data["accessToken"] as? String ?: return null
            val newRefresh = data["refreshToken"] as? String
            tokenManager.accessToken = newAccess
            if (newRefresh != null) tokenManager.refreshToken = newRefresh
            Timber.d("Token refreshed")
            newAccess
        } catch (e: Exception) {
            Timber.e(e, "Token refresh failed")
            onTokenExpired?.invoke()
            null
        }
    }

    private fun Request.withBearer(token: String?) = newBuilder().apply {
        if (token != null) header("Authorization", "Bearer $token")
    }.build()
}
