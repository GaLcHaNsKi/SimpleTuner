package dev.simpletuner.viewmodel

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.simpletuner.audio.AudioCapture
import dev.simpletuner.domain.InstrumentMode
import dev.simpletuner.domain.TuningPrecision
import dev.simpletuner.dsp.TrackedPitch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface TunerStatus {
    data class PermissionRequired(val permanentlyDenied: Boolean) : TunerStatus
    data object Starting : TunerStatus
    data object Listening : TunerStatus
    data object Uncertain : TunerStatus
    data class Tracking(val pitch: TrackedPitch.Tracking) : TunerStatus
    data object AudioUnavailable : TunerStatus
}

data class TunerUiState(val mode: InstrumentMode, val precision: TuningPrecision, val status: TunerStatus)

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("tuner", 0)
    private val initialMode = runCatching {
        InstrumentMode.valueOf(preferences.getString("mode", "GUITAR") ?: "GUITAR")
    }.getOrDefault(InstrumentMode.GUITAR)
    private val initialPrecision = if (preferences.getBoolean("precise", false)) TuningPrecision.PRECISE
        else TuningPrecision.STANDARD
    private val mutableState = MutableStateFlow(
        TunerUiState(initialMode, initialPrecision, TunerStatus.PermissionRequired(false)),
    )
    val state: StateFlow<TunerUiState> = mutableState.asStateFlow()
    private val capture = AudioCapture(application)
    private val captureMutex = Mutex()
    private var captureJob: Job? = null
    @Volatile private var generation = 0

    fun selectMode(mode: InstrumentMode) {
        select(mode, state.value.precision)
    }

    fun selectPrecision(precision: TuningPrecision) {
        select(state.value.mode, precision)
    }

    private fun select(mode: InstrumentMode, precision: TuningPrecision) {
        if (state.value.mode == mode && state.value.precision == precision) return
        val wasCapturing = captureJob?.isActive == true
        if (wasCapturing) stop()
        preferences.edit {
            putString("mode", mode.name)
            putBoolean("precise", precision == TuningPrecision.PRECISE)
        }
        mutableState.update { it.copy(mode = mode, precision = precision, status = when (it.status) {
            is TunerStatus.Tracking, TunerStatus.Uncertain -> TunerStatus.Listening
            else -> it.status
        }) }
        if (wasCapturing) start()
    }

    fun permissionRequired(permanent: Boolean) {
        stop()
        mutableState.update { it.copy(status = TunerStatus.PermissionRequired(permanent)) }
    }

    fun start() {
        if (captureJob?.isActive == true) return
        val thisGeneration = ++generation
        val settings = state.value
        mutableState.update { it.copy(status = TunerStatus.Starting) }
        captureJob = viewModelScope.launch {
            try {
                captureMutex.withLock {
                    withContext(Dispatchers.IO) {
                        capture.run(settings.mode, settings.precision) { pitch ->
                            mutableState.update { old ->
                                if (thisGeneration != generation) old else old.copy(status = when (pitch) {
                                    TrackedPitch.Listening -> TunerStatus.Listening
                                    TrackedPitch.Uncertain -> TunerStatus.Uncertain
                                    is TrackedPitch.Tracking -> TunerStatus.Tracking(pitch)
                                })
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                if (thisGeneration == generation) mutableState.update { it.copy(status = TunerStatus.PermissionRequired(false)) }
            } catch (_: Exception) {
                if (thisGeneration == generation) mutableState.update { it.copy(status = TunerStatus.AudioUnavailable) }
            }
        }
    }

    fun stop() {
        generation++
        captureJob?.cancel()
        captureJob = null
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
