package com.storelense.c66.data.remote

import com.storelense.c66.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<ApiResponse<LoginData>>

    /** Resolves an EAN barcode to all in_store EPCs for that product at the given store. */
    @GET("api/inventory/epc-by-ean/{ean}")
    suspend fun getEpcsByEan(
        @Path("ean") ean: String,
        @Query("storeId") storeId: String
    ): Response<ApiResponse<EpcsByEanResponse>>

    @POST("api/inventory/epc/sold")
    suspend fun markEpcsSold(@Body req: MarkEpcsSoldRequest): Response<ApiResponse<MarkEpcsSoldResponse>>
}
