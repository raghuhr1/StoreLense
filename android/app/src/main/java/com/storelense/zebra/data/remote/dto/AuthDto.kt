package com.storelense.zebra.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    @Json(name = "refreshToken") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val accessToken:  String,
    val refreshToken: String,
    val tokenType:    String,
    val expiresIn:    Long,
    val userId:       String,
    val username:     String,
    val role:         String,
    val storeId:      String?,
)
