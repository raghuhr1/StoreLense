package com.storelense.mobile.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSettings(
    val autoSync: Boolean = true,
    val intervalMinutes: Int = 60   // 15 | 60 | 360
)

@Singleton
class SyncSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load() = SyncSettings(
        autoSync        = prefs.getBoolean(KEY_AUTO_SYNC, true),
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 60)
    )

    fun save(settings: SyncSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTO_SYNC, settings.autoSync)
            .putInt(KEY_INTERVAL, settings.intervalMinutes)
            .apply()
    }

    companion object {
        private const val PREFS_NAME    = "sync_settings"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_INTERVAL  = "interval_minutes"
    }
}
