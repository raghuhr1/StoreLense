package com.storelense.zebra.data.remote

import com.storelense.zebra.data.remote.dto.*
import retrofit2.http.*

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiResponse<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body body: RefreshRequest): ApiResponse<Unit>

    // ── Stores ────────────────────────────────────────────────────────────────

    @GET("api/stores")
    suspend fun listStores(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): ApiResponse<PageDto<StoreDto>>

    @GET("api/stores/{storeId}/zones")
    suspend fun listZones(@Path("storeId") storeId: String): ApiResponse<List<ZoneDto>>

    // ── SOH ───────────────────────────────────────────────────────────────────

    @GET("api/soh/sessions")
    suspend fun listSessions(
        @Query("storeId") storeId: String,
        @Query("status")  status:  String? = null,
        @Query("page")    page:    Int = 0,
        @Query("size")    size:    Int = 20,
    ): ApiResponse<PageDto<SohSessionDto>>

    @POST("api/soh/sessions")
    suspend fun createSession(@Body body: CreateSohSessionRequest): ApiResponse<SohSessionDto>

    @POST("api/soh/sessions/{id}/complete")
    suspend fun completeSession(@Path("id") id: String): ApiResponse<SohResultDto>

    @POST("api/soh/sessions/{id}/cancel")
    suspend fun cancelSession(
        @Path("id")    id:     String,
        @Query("reason") reason: String? = null,
    ): ApiResponse<Unit>

    // ── RFID Ingest ───────────────────────────────────────────────────────────

    @POST("api/rfid/ingest/batch")
    suspend fun ingestBatch(@Body body: RfidReadBatchRequest): ApiResponse<RfidBatchResponse>

    // ── Refill Tasks ──────────────────────────────────────────────────────────

    @GET("api/refill/tasks")
    suspend fun listRefillTasks(
        @Query("storeId") storeId: String,
        @Query("status")  status:  String? = null,
        @Query("page")    page:    Int = 0,
        @Query("size")    size:    Int = 20,
    ): ApiResponse<PageDto<RefillTaskDto>>

    @GET("api/refill/tasks/{id}")
    suspend fun getRefillTask(@Path("id") id: String): ApiResponse<RefillTaskDto>

    @PATCH("api/refill/tasks/{taskId}/items/{itemId}/fulfil")
    suspend fun fulfilItem(
        @Path("taskId")   taskId:   String,
        @Path("itemId")   itemId:   String,
        @Query("quantity") quantity: Int,
    ): ApiResponse<RefillTaskDto>
}
