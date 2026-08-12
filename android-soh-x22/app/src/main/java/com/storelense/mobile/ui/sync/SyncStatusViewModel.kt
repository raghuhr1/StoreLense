package com.storelense.mobile.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.storelense.mobile.data.local.dao.EpcReadDao
import com.storelense.mobile.data.local.dao.TransferDao
import com.storelense.mobile.work.ProductSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class SyncStatus { IDLE, SYNCING, FAILED }

data class SyncStatusState(
    val status: SyncStatus = SyncStatus.IDLE,
    val pendingCount: Int = 0,
    val lastSyncAt: String? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val transferDao: TransferDao,
    private val epcReadDao: EpcReadDao
) : ViewModel() {

    private val _state = MutableStateFlow(SyncStatusState())
    val state = _state.asStateFlow()

    init {
        observeWorkers()
        observePending()
    }

    private fun observeWorkers() {
        workManager.getWorkInfosForUniqueWorkLiveData(ProductSyncWorker.WORK_NAME_PERIODIC)
            .asFlow()
            .onEach { infos ->
                val running = infos.any { it.state == WorkInfo.State.RUNNING }
                val failed  = infos.any { it.state == WorkInfo.State.FAILED }
                _state.update { it.copy(
                    isSyncing = running,
                    status    = when {
                        running -> SyncStatus.SYNCING
                        failed  -> SyncStatus.FAILED
                        else    -> SyncStatus.IDLE
                    }
                ) }
            }
            .launchIn(viewModelScope)
    }

    private fun observePending() {
        combine(
            transferDao.countPendingFlow(),
            epcReadDao.countAllPendingFlow()
        ) { transfers, epcs -> transfers + epcs }
            .onEach { total -> _state.update { it.copy(pendingCount = total) } }
            .launchIn(viewModelScope)
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            workManager.enqueueUniqueWork(
                "manual_sync_product",
                ExistingWorkPolicy.REPLACE,
                ProductSyncWorker.buildOneTime()
            )
            _state.update { it.copy(
                lastSyncAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            ) }
        }
    }
}
