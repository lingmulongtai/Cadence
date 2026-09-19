package dev.lingmulongtai.cadence.overlay.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RecordingPhase { IDLE, STARTING, RECORDING, STOPPING, FINISHED, FAILED }

data class RecordingState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val path: String? = null,
    val eventCount: Long = 0,
    val elapsedSeconds: Long = 0,
    val sensors: List<String> = emptyList(),
    val gpsStatus: String = "off",
    val error: String? = null,
) {
    val isActive: Boolean get() = phase == RecordingPhase.STARTING || phase == RecordingPhase.RECORDING || phase == RecordingPhase.STOPPING
}

/** Shared state only. The service, never an Activity, owns the actual subscriptions. */
@Singleton
class RecordingController @Inject constructor(@param:ApplicationContext private val context: Context) {
    internal val mutableState = MutableStateFlow(RecordingState())
    val state = mutableState.asStateFlow()

    fun start(highPrecision: Boolean, includeGps: Boolean) {
        if (state.value.isActive) return
        mutableState.value = RecordingState(phase = RecordingPhase.STARTING)
        try {
            ContextCompat.startForegroundService(context,
                Intent(context, SensorRecordingService::class.java)
                    .setAction(SensorRecordingService.START)
                    .putExtra(SensorRecordingService.HIGH_PRECISION, highPrecision)
                    .putExtra(SensorRecordingService.GPS, includeGps))
        } catch (error: RuntimeException) {
            mutableState.value = RecordingState(phase = RecordingPhase.FAILED, error = error.message)
        }
    }

    fun stop() {
        if (!state.value.isActive) return
        try {
            context.startService(Intent(context, SensorRecordingService::class.java).setAction(SensorRecordingService.STOP))
        } catch (error: RuntimeException) {
            mutableState.value = state.value.copy(error = error.message)
        }
    }
}
