package com.storelense.mobile.ui.locator

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.rfid.RfidReader
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeigerState(
    val targetEpc: String       = "",
    val currentRssi: Double     = -100.0,
    val rssiHistory: List<Double> = emptyList(),   // up to 20 samples, oldest first
    val proximityLabel: String  = "FAR",
    val speakerEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val isScanning: Boolean     = false
)

@HiltViewModel
class GeigerLocatorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val rfid: RfidReader
) : ViewModel() {

    private val targetEpc: String = savedState["epc"] ?: ""

    private val _state = MutableStateFlow(GeigerState(targetEpc = targetEpc))
    val state = _state.asStateFlow()

    private val rssiBuffer = ArrayDeque<Double>(20)

    private val toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (_: RuntimeException) { null }

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(VibratorManager::class.java))!!.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)!!
        }

    private var beepJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                rfid.connect()
                rfid.setTxPower(30)
                rfid.startScan()
            } catch (e: Exception) {
                Timber.e(e, "RFID connect failed")
                _state.update { it.copy(isScanning = false) }
                return@launch
            }
            _state.update { it.copy(isScanning = true) }
            rfid.reads.collect { read ->
                if (read.epc != targetEpc) return@collect
                val rssi = read.rssi ?: return@collect
                if (rssiBuffer.size >= 20) rssiBuffer.removeFirst()
                rssiBuffer.addLast(rssi)
                _state.update { s ->
                    s.copy(
                        currentRssi    = rssi,
                        rssiHistory    = rssiBuffer.toList(),
                        proximityLabel = rssiToLabel(rssi)
                    )
                }
            }
        }
        startBeepLoop()
    }

    private fun startBeepLoop() {
        beepJob?.cancel()
        beepJob = viewModelScope.launch {
            while (isActive) {
                val s = _state.value
                if (s.speakerEnabled) {
                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                }
                if (s.vibrateEnabled) {
                    vibratePulse()
                }
                delay(beepInterval(s.currentRssi, s.rssiHistory.isEmpty()))
            }
        }
    }

    private fun beepInterval(rssi: Double, noData: Boolean): Long = when {
        noData      -> 3000L
        rssi >= -50 -> 200L
        rssi >= -60 -> 400L
        rssi >= -70 -> 800L
        rssi >= -80 -> 1500L
        else        -> 3000L
    }

    private fun rssiToLabel(rssi: Double): String = when {
        rssi >= -50 -> "VERY CLOSE"
        rssi >= -65 -> "NEAR"
        rssi >= -75 -> "GETTING CLOSER"
        else        -> "FAR"
    }

    private fun vibratePulse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }

    fun toggleSpeaker() = _state.update { it.copy(speakerEnabled = !it.speakerEnabled) }

    fun toggleVibrate() = _state.update { it.copy(vibrateEnabled = !it.vibrateEnabled) }

    fun stop() {
        beepJob?.cancel()
        beepJob = null
        viewModelScope.launch {
            rfid.stopScan()
            rfid.disconnect()
        }
        _state.update { it.copy(isScanning = false) }
    }

    override fun onCleared() {
        super.onCleared()
        beepJob?.cancel()
        try { toneGen?.release() } catch (_: Exception) {}
        viewModelScope.launch {
            try { rfid.stopScan(); rfid.disconnect() } catch (_: Exception) {}
        }
    }
}
