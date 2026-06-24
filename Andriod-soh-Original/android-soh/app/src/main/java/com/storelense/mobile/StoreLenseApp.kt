package com.storelense.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.storelense.mobile.work.ProductSyncWorker
import com.storelense.mobile.work.RefillSyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class StoreLenseApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        scheduleBackgroundWork()
    }

    private fun scheduleBackgroundWork() {
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniquePeriodicWork(
            ProductSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            ProductSyncWorker.buildPeriodic()
        )
        wm.enqueueUniquePeriodicWork(
            "refill_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            RefillSyncWorker.buildPeriodic()
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
