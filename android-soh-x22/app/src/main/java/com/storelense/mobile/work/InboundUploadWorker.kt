package com.storelense.mobile.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.storelense.mobile.data.repository.InboundRepository
import com.storelense.mobile.data.repository.Result
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class InboundUploadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: InboundRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val shipmentId = inputData.getString(KEY_SHIPMENT_ID) ?: return Result.failure()
        Timber.d("InboundUploadWorker: receiving shipment $shipmentId")
        return when (val r = repo.receiveShipment(shipmentId)) {
            is com.storelense.mobile.data.repository.Result.Success -> Result.success()
            is com.storelense.mobile.data.repository.Result.Error -> {
                Timber.w("InboundUploadWorker: ${r.message}")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        const val KEY_SHIPMENT_ID = "shipment_id"

        fun build(shipmentId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<InboundUploadWorker>()
                .setInputData(workDataOf(KEY_SHIPMENT_ID to shipmentId))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
    }
}
