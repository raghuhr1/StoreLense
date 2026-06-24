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
        val forceFull = inputData.getBoolean("forceFull", false)
        
        Timber.d("ProductSyncWorker: starting sync for store $storeId (forceFull=$forceFull)")
        return when (val r = repo.syncProducts(storeId, forceFull)) {
            is com.storelense.mobile.data.repository.Result.Success -> {
                Timber.i("ProductSyncWorker: successfully synced ${r.data} products")
                Result.success()
            }
            is com.storelense.mobile.data.repository.Result.Error -> {
                Timber.e("ProductSyncWorker failed: ${r.message}")
                // If it's a "Products API error: 404", don't retry indefinitely
                if (r.message.contains("404")) Result.failure() else Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "product_catalog_sync_periodic"
        const val WORK_NAME_MANUAL   = "product_catalog_sync_manual"

        fun buildPeriodic(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ProductSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun buildOneTime(forceFull: Boolean = false): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ProductSyncWorker>()
                .setInputData(workDataOf("forceFull" to forceFull))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
    }
}
