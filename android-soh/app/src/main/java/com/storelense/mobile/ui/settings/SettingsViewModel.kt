package com.storelense.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ReaderSettings
import com.storelense.mobile.data.repository.ReaderSettingsRepository
import com.storelense.mobile.data.repository.SyncSettings
import com.storelense.mobile.data.repository.SyncSettingsRepository
import com.storelense.mobile.work.ProductSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsState(
    val username: String = "",
    val role: String = "",
    val readerSettings: ReaderSettings = ReaderSettings(),
    val syncSettings: SyncSettings = SyncSettings()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val readerPrefs: ReaderSettingsRepository,
    private val syncPrefs: SyncSettingsRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            username       = auth.username ?: "",
            role           = auth.role?.removePrefix("ROLE_") ?: "",
            readerSettings = readerPrefs.load(),
            syncSettings   = syncPrefs.load()
        )
    )
    val state = _state.asStateFlow()

    fun saveReaderSettings(settings: ReaderSettings) {
        readerPrefs.save(settings)
        _state.update { it.copy(readerSettings = settings) }
    }

    fun saveSyncSettings(settings: SyncSettings) {
        syncPrefs.save(settings)
        _state.update { it.copy(syncSettings = settings) }
        viewModelScope.launch { applyWorkSchedule(settings) }
    }

    fun logout() = auth.logout()

    private fun applyWorkSchedule(settings: SyncSettings) {
        if (settings.autoSync) {
            workManager.enqueueUniquePeriodicWork(
                ProductSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequestBuilder<ProductSyncWorker>(
                    settings.intervalMinutes.toLong(), TimeUnit.MINUTES
                )
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                    .build()
            )
        } else {
            workManager.cancelUniqueWork(ProductSyncWorker.WORK_NAME)
            workManager.cancelUniqueWork("refill_sync")
        }
    }
}
