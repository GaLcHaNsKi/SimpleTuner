package dev.simpletuner.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.SystemClock
import dev.simpletuner.domain.InstrumentMode
import dev.simpletuner.domain.TuningPrecision
import dev.simpletuner.dsp.PitchTracker
import dev.simpletuner.dsp.TrackedPitch
import dev.simpletuner.dsp.YinPitchDetector
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

class AudioCapture(private val context: Context) {
    /** Called from an IO dispatcher after RECORD_AUDIO has been granted. */
    @SuppressLint("MissingPermission")
    suspend fun run(mode: InstrumentMode, precision: TuningPrecision, onUpdate: (TrackedPitch) -> Unit) {
        val manager = context.getSystemService(AudioManager::class.java)
        val unprocessed = manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = if (unprocessed) listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
            else listOf(MediaRecorder.AudioSource.MIC)
        var lastError: Exception? = null
        for (source in sources) for (rate in listOf(48000, 44100)) {
            if (!currentCoroutineContext().isActive) return
            try {
                capture(source, rate, mode, precision, onUpdate)
                return
            } catch (error: SecurityException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Audio input unavailable")
    }

    @SuppressLint("MissingPermission")
    private suspend fun capture(source: Int, rate: Int, mode: InstrumentMode,
                                precision: TuningPrecision, onUpdate: (TrackedPitch) -> Unit) {
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuffer > 0) { "Unsupported sample rate" }
        val recorder = AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 8192 * 2))
        var agc: AutomaticGainControl? = null
        var ns: NoiseSuppressor? = null
        var aec: AcousticEchoCanceler? = null
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Cannot initialize microphone" }
            // Some devices refuse effect control; capture is still usable in that case.
            runCatching { if (AutomaticGainControl.isAvailable()) agc = AutomaticGainControl.create(recorder.audioSessionId)?.apply { enabled = false } }
            runCatching { if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = false } }
            runCatching { if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = false } }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Cannot start microphone" }
            val detector = YinPitchDetector(rate)
            val tracker = PitchTracker(precision)
            val window = ShortArray(8192)
            val block = ShortArray(2048)
            var filled = 0
            onUpdate(TrackedPitch.Listening)
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                check(read > 0) { "AudioRecord read failed: $read" }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recorder.activeRecordingConfiguration?.isClientSilenced == true) {
                    error("Microphone is silenced by the system")
                }
                var offset = 0
                while (offset < read) {
                    val count = minOf(read - offset, window.size - filled)
                    block.copyInto(window, filled, offset, offset + count)
                    filled += count
                    offset += count
                    if (filled == window.size) {
                        onUpdate(tracker.update(detector.detect(window), mode, SystemClock.elapsedRealtime()))
                        window.copyInto(window, 0, block.size, window.size)
                        filled -= block.size
                    }
                }
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { agc?.release() }
            runCatching { ns?.release() }
            runCatching { aec?.release() }
            recorder.release()
        }
    }
}
