package com.storelense.epcencoder.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse<LoginResponse>>

    // Excel/CSV "EAN-13" values were stored directly in products.sku for this product batch,
    // so lookup by-sku with the EAN value (confirmed against prod DB; no by-ean-returning-product endpoint exists).
    @GET("api/products/by-sku/{sku}")
    suspend fun getProductBySku(@Path("sku") sku: String): Response<ApiResponse<ProductResponse>>

    @POST("api/products/{id}/epc")
    suspend fun associateEpc(
        @Path("id") productId: String,
        @Query("epc") epc: String,
        @Header("Authorization") authHeader: String
    ): Response<ApiResponse<Unit>>
}
