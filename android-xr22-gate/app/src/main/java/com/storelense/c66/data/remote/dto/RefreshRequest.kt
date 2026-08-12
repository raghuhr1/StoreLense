package com.storelense.c66.data.remote.dto

data class RefreshRequest(val refreshToken: String)
data class RefreshData(val accessToken: String, val refreshToken: String)
