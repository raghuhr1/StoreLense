package com.storelense.mobile.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ReplenishRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class RefillSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: ReplenishRepository,
    private val auth: AuthRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val storeId = auth.storeId ?: return Result.success()
        Timber.d("RefillSyncWorker: syncing tasks for store $storeId")
        repo.refreshTasks(storeId)
        return Result.success()
    }

    companion object {
        fun buildPeriodic(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<RefillSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
    }
}
