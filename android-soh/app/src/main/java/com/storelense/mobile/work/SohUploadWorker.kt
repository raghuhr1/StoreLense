package com.storelense.mobile.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.SohRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SohUploadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val soh: SohRepository,
    private val auth: AuthRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val storeId   = auth.storeId ?: return Result.failure()
        Timber.d("SohUploadWorker: uploading session $sessionId")
        return when (val r = soh.uploadBatch(sessionId, storeId)) {
            is com.storelense.mobile.data.repository.Result.Success -> {
                Timber.d("SohUploadWorker: upload success")
                Result.success()
            }
            is com.storelense.mobile.data.repository.Result.Error -> {
                Timber.w("SohUploadWorker: ${r.message}")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"

        fun build(sessionId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SohUploadWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
    }
}
