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
    suspend fun getSohSession(
        @Path("id") id: String,
        @Query("includeEpcs") includeEpcs: Boolean = false
    ): Response<ApiResponse<SohSessionDto>>

    @GET("api/soh/sessions/{id}/epcs")
    suspend fun getSohSessionEpcs(@Path("id") id: String): Response<ApiResponse<List<String>>>

    @GET("api/soh/sessions/{id}/expected-epcs")
    suspend fun getSohSessionExpectedEpcs(@Path("id") id: String): Response<ApiResponse<List<String>>>

    @POST("api/soh/sessions")
    suspend fun createSohSession(@Body req: CreateSohSessionRequest): Response<ApiResponse<SohSessionDto>>

    @POST("api/rfid/ingest/batch")
    suspend fun ingestRfidBatch(@Body req: RfidBatchRequest): Response<ApiResponse<Unit>>

    @POST("api/soh/sessions/{id}/complete")
    suspend fun completeSohSession(@Path("id") id: String): Response<ApiResponse<SohSessionDto>>

    // ── Phase 5: Session Participants ─────────────────────────────────────────────

    @POST("api/soh/sessions/{id}/participants")
    suspend fun joinSohSession(
        @Path("id") id: String,
        @Body req: JoinSessionRequest
    ): Response<ApiResponse<ParticipantDto>>

    @POST("api/soh/sessions/{id}/participants/{deviceId}/done")
    suspend fun markParticipantDone(
        @Path("id")       id: String,
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<MarkDoneDto>>

    @GET("api/soh/sessions/{id}/participants")
    suspend fun getSohParticipants(
        @Path("id") id: String
    ): Response<ApiResponse<ParticipantsListDto>>

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

    // ── Products ──────────────────────────────────────────────────────────────

    @GET("api/products")
    suspend fun getProducts(
        @Query("storeId") storeId: String,
        @Query("search")  search: String?  = null,
        @Query("page")    page: Int        = 0,
        @Query("size")    size: Int        = 200,
        @Query("sync")    sync: Boolean    = false,
        @Query("since")   since: Long?     = null
    ): Response<ApiResponse<PagedData<ProductDto>>>

    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: String): Response<ApiResponse<ProductDto>>

    @GET("api/inventory/epc/{epc}")
    suspend fun getEpcLocation(@Path("epc") epc: String): Response<ApiResponse<EpcLocationDto>>

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

    @PATCH("api/refill/tasks/{taskId}/items/{itemId}/fulfil")
    suspend fun fulfilItem(
        @Path("taskId") taskId: String,
        @Path("itemId") itemId: String,
        @Body req: FulfilItemRequest
    ): Response<ApiResponse<RefillTaskItemDto>>

    @POST("api/refill/tasks/{id}/complete")
    suspend fun completeRefillTask(@Path("id") id: String): Response<ApiResponse<RefillTaskDto>>

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GET("api/reporting/dashboard/summary")
    suspend fun getDashboardSummary(
        @Query("storeId") storeId: String,
        @Query("days")    days: Int = 7
    ): Response<ApiResponse<DashboardSummaryDto>>

    // ── Stores & Zones ────────────────────────────────────────────────────────

    @GET("api/stores")
    suspend fun getStores(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 200
    ): Response<ApiResponse<PagedData<StoreDto>>>

    @GET("api/stores/{storeId}/zones")
    suspend fun getZones(
        @Path("storeId") storeId: String
    ): Response<ApiResponse<List<ZoneDto>>>

    // ── Transfers ─────────────────────────────────────────────────────────────

    @POST("api/transfers")
    suspend fun createTransfer(@Body req: CreateTransferRequest): Response<ApiResponse<TransferDto>>

    @GET("api/transfers/{id}")
    suspend fun getTransfer(@Path("id") id: String): Response<ApiResponse<TransferDto>>

    @POST("api/transfers/{id}/receive")
    suspend fun receiveTransfer(
        @Path("id") id: String,
        @Body req: ReceiveTransferRequest
    ): Response<ApiResponse<TransferDto>>

    // ── Exceptions ────────────────────────────────────────────────────────────

    @GET("api/exceptions/summary")
    suspend fun getExceptionsSummary(
        @Query("storeId") storeId: String
    ): Response<ApiResponse<ExceptionSummaryDto>>

    @GET("api/exceptions")
    suspend fun getExceptions(
        @Query("storeId") storeId: String,
        @Query("type")    type: String,
        @Query("page")    page: Int = 0,
        @Query("size")    size: Int = 50
    ): Response<ApiResponse<PagedData<ExceptionItemDto>>>

    @GET("api/exceptions/ghost/{epc}")
    suspend fun getGhostDetail(@Path("epc") epc: String): Response<ApiResponse<GhostAnalysisDetailDto>>

    @POST("api/exceptions/ghost/{epc}/ignore")
    suspend fun ignoreGhost(@Path("epc") epc: String): Response<ApiResponse<Unit>>

    @POST("api/exceptions/ghost/{epc}/investigate")
    suspend fun investigateGhost(@Path("epc") epc: String): Response<ApiResponse<Unit>>

    @GET("api/exceptions/missing/{epc}")
    suspend fun getMissingDetail(@Path("epc") epc: String): Response<ApiResponse<MissingEpcDetailDto>>

    @POST("api/exceptions/missing/{epc}/mark-missing")
    suspend fun markMissing(@Path("epc") epc: String): Response<ApiResponse<Unit>>

    // ── Inventory Lookup ──────────────────────────────────────────────────────

    @GET("api/inventory/state")
    suspend fun getInventoryState(
        @Query("storeId") storeId: String
    ): Response<ApiResponse<List<InventoryStateDto>>>

    @GET("api/inventory/sku/{sku}")
    suspend fun getInventoryBySku(
        @Path("sku")      sku: String,
        @Query("storeId") storeId: String
    ): Response<ApiResponse<InventorySkuDto>>

    // ── Tag Items ─────────────────────────────────────────────────────────────

    @POST("api/inventory/commission")
    suspend fun commissionTagItem(
        @Body req: CommissionTagRequest
    ): Response<ApiResponse<CommissionTagResponse>>
}
