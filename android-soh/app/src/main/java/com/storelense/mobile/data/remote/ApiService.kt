package com.storelense.mobile.data.remote

import com.storelense.mobile.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ─────────────────────────────────────────────────────────────────

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<ApiResponse<LoginData>>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): Response<ApiResponse<LoginData>>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    // ── SOH ──────────────────────────────────────────────────────────────────

    @GET("api/soh/sessions")
    suspend fun getSohSessions(
        @Query("storeId") storeId: String,
        @Query("status")  status: String  = "in_progress,pending",
        @Query("page")    page: Int       = 0,
        @Query("size")    size: Int       = 50
    ): Response<ApiResponse<PagedData<SohSessionDto>>>

    @GET("api/soh/sessions/{id}")
    suspend fun getSohSession(@Path("id") id: String): Response<ApiResponse<SohSessionDto>>

    @POST("api/soh/sessions")
    suspend fun createSohSession(@Body req: CreateSohSessionRequest): Response<ApiResponse<SohSessionDto>>

    @POST("api/rfid/ingest/batch")
    suspend fun ingestRfidBatch(@Body req: RfidBatchRequest): Response<ApiResponse<Unit>>

    @POST("api/soh/sessions/{id}/complete")
    suspend fun completeSohSession(@Path("id") id: String): Response<ApiResponse<SohSessionDto>>

    // ── Inbound ───────────────────────────────────────────────────────────────
    // NOTE: /api/inbound/* endpoints require backend implementation in inventory-service

    @GET("api/inbound/shipments")
    suspend fun getShipments(
        @Query("storeId") storeId: String,
        @Query("status")  status: String = "expected",
        @Query("page")    page: Int      = 0,
        @Query("size")    size: Int      = 50
    ): Response<ApiResponse<PagedData<InboundShipmentDto>>>

    @GET("api/inbound/shipments/{id}")
    suspend fun getShipment(@Path("id") id: String): Response<ApiResponse<InboundShipmentDto>>

    @POST("api/inbound/shipments/{id}/receive")
    suspend fun receiveShipment(
        @Path("id") id: String,
        @Body req: InboundReceiveRequest
    ): Response<ApiResponse<InboundResultDto>>

    // ── Replenishment ─────────────────────────────────────────────────────────

    @GET("api/refill/tasks")
    suspend fun getRefillTasks(
        @Query("storeId") storeId: String,
        @Query("status")  status: String = "pending,in_progress",
        @Query("page")    page: Int      = 0,
        @Query("size")    size: Int      = 50
    ): Response<ApiResponse<PagedData<RefillTaskDto>>>

    @GET("api/refill/tasks/{id}")
    suspend fun getRefillTask(@Path("id") id: String): Response<ApiResponse<RefillTaskDto>>

    @POST("api/refill/tasks/{taskId}/items/{itemId}/fulfil")
    suspend fun fulfilItem(
        @Path("taskId") taskId: String,
        @Path("itemId") itemId: String,
        @Body req: FulfilItemRequest
    ): Response<ApiResponse<RefillTaskItemDto>>

    @POST("api/refill/tasks/{id}/complete")
    suspend fun completeRefillTask(@Path("id") id: String): Response<ApiResponse<RefillTaskDto>>
}
