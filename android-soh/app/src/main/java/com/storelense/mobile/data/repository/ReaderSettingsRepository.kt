package com.storelense.mobile.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ReaderSettings(
    val txPowerDbm: Int = 20,
    val scanMode: String = "SINGLE_TARGET",   // SINGLE_TARGET | DUAL_TARGET
    val buzzerEnabled: Boolean = true
)

@Singleton
class ReaderSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load() = ReaderSettings(
        txPowerDbm    = prefs.getInt(KEY_TX_POWER, 20),
        scanMode      = prefs.getString(KEY_SCAN_MODE, "SINGLE_TARGET") ?: "SINGLE_TARGET",
        buzzerEnabled = prefs.getBoolean(KEY_BUZZER, true)
    )

    fun save(settings: ReaderSettings) {
        prefs.edit()
            .putInt(KEY_TX_POWER, settings.txPowerDbm)
            .putString(KEY_SCAN_MODE, settings.scanMode)
            .putBoolean(KEY_BUZZER, settings.buzzerEnabled)
            .apply()
    }

    companion object {
        private const val PREFS_NAME    = "reader_settings"
        private const val KEY_TX_POWER  = "tx_power_dbm"
        private const val KEY_SCAN_MODE = "scan_mode"
        private const val KEY_BUZZER    = "buzzer_enabled"
    }
}
