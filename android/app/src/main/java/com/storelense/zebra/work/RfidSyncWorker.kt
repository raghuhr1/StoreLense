package com.storelense.zebra.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.data.remote.TokenManager
import com.storelense.zebra.domain.repository.RfidRepository
import com.storelense.zebra.domain.repository.RefillRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class RfidSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params:     WorkerParameters,
    private val rfidRepo:   RfidRepository,
    private val refillRepo: RefillRepository,
    private val tokenMgr:   TokenManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val storeId   = tokenMgr.getStoreId()              ?: return Result.failure()
        val deviceId  = android.os.Build.SERIAL.ifBlank { "unknown" }

        Timber.d("RfidSyncWorker: uploading reads for session $sessionId")

        return when (val r = rfidRepo.uploadPendingReads(sessionId, storeId, deviceId)) {
            is NetworkResult.Success -> {
                Timber.d("RfidSyncWorker: uploaded ${r.data} reads")
                Result.success()
            }
            is NetworkResult.Error   -> {
                Timber.w("RfidSyncWorker: failed — ${r.message}")
                if (runAttemptCount < 4) Result.retry() else Result.failure()
            }
            is NetworkResult.Loading -> Result.retry()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"

        fun buildRequest(sessionId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<RfidSyncWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

        /** Periodic background sync — syncs refill tasks every 15 minutes when online */
        fun periodicRefillSync(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<RefillSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
    }
}

@HiltWorker
class RefillSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params:     WorkerParameters,
    private val refillRepo: RefillRepository,
    private val tokenMgr:   TokenManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val storeId = tokenMgr.getStoreId() ?: return Result.success()
        return when (refillRepo.syncTasks(storeId)) {
            is NetworkResult.Success -> Result.success()
            is NetworkResult.Error   -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            is NetworkResult.Loading -> Result.retry()
        }
    }
}
