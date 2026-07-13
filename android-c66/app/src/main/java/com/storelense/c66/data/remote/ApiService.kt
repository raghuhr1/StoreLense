package com.storelense.c66.data.remote

import com.storelense.c66.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<ApiResponse<LoginData>>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): Response<ApiResponse<RefreshData>>

    @GET("api/inventory/epc-by-ean/{ean}")
    suspend fun getEpcsByEan(
        @Path("ean") ean: String,
        @Query("storeId") storeId: String
    ): Response<ApiResponse<EpcsByEanResponse>>

    @POST("api/inventory/epc/sold")
    suspend fun markEpcsSold(@Body req: MarkEpcsSoldRequest): Response<ApiResponse<MarkEpcsSoldResponse>>

    @POST("api/gate/checks")
    suspend fun recordGateCheck(@Body req: GateCheckRequest): Response<ApiResponse<Unit>>

    @GET("api/gate/checks/bills/{billRef}")
    suspend fun lookupBill(
        @Path("billRef") billRef: String,
        @Query("storeId") storeId: String
    ): Response<ApiResponse<BillLookupResponse>>
}
