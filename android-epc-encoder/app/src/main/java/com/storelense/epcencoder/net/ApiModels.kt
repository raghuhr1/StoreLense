package com.storelense.epcencoder.net

data class ApiResponse<T>(
    val success: Boolean?,
    val message: String?,
    val data: T?
)

data class LoginRequest(val username: String, val password: String)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val userId: String,
    val username: String,
    val role: String,
    val storeId: String?
)

data class ProductResponse(
    val id: String,
    val sku: String,
    val name: String?
)
