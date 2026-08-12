package com.storelense.c66.data.repository

import com.storelense.c66.data.remote.ApiService
import com.storelense.c66.data.remote.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreConfigRepository @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {
    private val _config = MutableStateFlow(StoreConfig.DEFAULTS)
    val config: StateFlow<StoreConfig> = _config.asStateFlow()

    suspend fun fetchAndCache() {
        val storeId = tokenManager.storeId ?: return
        try {
            val resp = api.getStoreFeatures(storeId)
            if (resp.isSuccessful) {
                val flags = resp.body()?.data
                    ?.associate { it.feature to it.enabled }
                    ?: return
                _config.value = StoreConfig(
                    cameraEnabled      = flags["GATE_CAMERA_SCANNER"] ?: false,
                    rfidVerifyEnabled  = flags["GATE_RFID_VERIFY"]    ?: true,
                    strictMode         = flags["GATE_STRICT_MODE"]    ?: false,
                    manualEntryEnabled = flags["GATE_MANUAL_ENTRY"]   ?: true
                )
            }
        } catch (_: Exception) {
            // network error — keep current defaults, no crash
        }
    }

    fun reset() { _config.value = StoreConfig.DEFAULTS }
}
