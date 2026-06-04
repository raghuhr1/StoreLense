package com.storelense.zebra.data.remote

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}

suspend fun <T> safeApiCall(block: suspend () -> com.storelense.zebra.data.remote.dto.ApiResponse<T>): NetworkResult<T> =
    runCatching { block() }
        .fold(
            onSuccess = { resp ->
                if (resp.success && resp.data != null) NetworkResult.Success(resp.data)
                else NetworkResult.Error(resp.message ?: "Unknown error")
            },
            onFailure = { NetworkResult.Error(it.message ?: "Network error") }
        )
