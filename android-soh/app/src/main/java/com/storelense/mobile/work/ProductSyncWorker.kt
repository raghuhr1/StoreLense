package com.storelense.mobile.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ProductRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class ProductSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: ProductRepository,
    private val auth: AuthRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val storeId = auth.storeId ?: return Result.success()
        Timber.d("ProductSyncWorker: syncing catalog for store $storeId")
        return when (val r = repo.syncProducts(storeId)) {
            is com.storelense.mobile.data.repository.Result.Success ->
                { Timber.d("ProductSyncWorker: synced ${r.data} products"); Result.success() }
            is com.storelense.mobile.data.repository.Result.Error ->
                { Timber.w("ProductSyncWorker: ${r.message}"); Result.retry() }
        }
    }

    companion object {
        const val WORK_NAME = "product_catalog_sync"

        fun buildPeriodic(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ProductSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun buildOneTime(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ProductSyncWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
    }
}
