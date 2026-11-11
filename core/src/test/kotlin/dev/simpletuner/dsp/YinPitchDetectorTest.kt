package dev.simpletuner.dsp

import dev.simpletuner.domain.InstrumentMode
import dev.simpletuner.domain.centsBetween
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.pow
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class YinPitchDetectorTest {
    private fun signal(rate: Int, frequency: Double, harmonic: Double = 0.0, amplitude: Double = 0.2,
                       phase: Double = 0.0, noise: Double = 0.0): ShortArray {
        val random = Random(42)
        return ShortArray(8192) { i ->
            val t = 2 * PI * frequency * i / rate + phase
            val value = amplitude * (sin(t) + harmonic * sin(2 * t)) + noise * random.nextDouble(-1.0, 1.0)
            (value.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
    }

    @Test fun `all ten strings measured accurately at both sample rates and phases`() {
        for (rate in listOf(44100, 48000)) {
            val detector = YinPitchDetector(rate)
            for (mode in InstrumentMode.entries) for (string in mode.strings) for (phase in listOf(0.0, 1.2)) {
                val result = detector.detect(signal(rate, string.frequencyHz, phase = phase))
                assertTrue("$rate ${string.note}${string.octave}: $result", result is PitchResult.Pitch)
                result as PitchResult.Pitch
                assertTrue("$rate ${string.note}${string.octave}: $result",
                    abs(centsBetween(result.frequencyHz, string.frequencyHz)) <= 1.0)
            }
        }
    }

    @Test fun `strong second harmonic does not produce an octave error`() {
        for (rate in listOf(44100, 48000)) for (frequency in listOf(41.2034, 82.4069, 110.0, 329.6276)) {
            val result = YinPitchDetector(rate).detect(signal(rate, frequency, harmonic = 4.0, amplitude = 0.1))
            assertTrue("$frequency $result", result is PitchResult.Pitch)
            assertTrue("$frequency $result", abs(centsBetween((result as PitchResult.Pitch).frequencyHz, frequency)) < 1.0)
        }
    }

    @Test fun `silence weak signal noise and out of range do not produce pitch`() {
        val detector = YinPitchDetector(48000)
        assertEquals(PitchResult.Silence, detector.detect(ShortArray(8192)))
        assertEquals(PitchResult.Silence, detector.detect(signal(48000, 110.0, amplitude = 0.001)))
        assertEquals(PitchResult.Uncertain, detector.detect(signal(48000, 110.0, amplitude = 0.0, noise = 0.2)))
        for (frequency in listOf(20.0, 30.0, 500.0, 800.0)) {
            assertEquals("$frequency", PitchResult.Uncertain, detector.detect(signal(48000, frequency)))
        }
    }

    @Test fun `detuned strings are measured accurately`() {
        val detector = YinPitchDetector(48000)
        for (mode in InstrumentMode.entries) for (string in mode.strings) {
            for (offset in listOf(-100.0, -12.0, 7.0, 100.0)) {
                val hz = string.frequencyHz * 2.0.pow(offset / 1200.0)
                val pitch = detector.detect(signal(48000, hz, phase = 0.8))
                assertTrue("$hz: $pitch", pitch is PitchResult.Pitch)
                assertTrue("$hz: $pitch", abs(centsBetween((pitch as PitchResult.Pitch).frequencyHz, hz)) <= 1.0)
            }
        }
    }

    @Test fun `noisy signal retains accurate pitch`() {
        val result = YinPitchDetector(48000).detect(signal(48000, 110.0, amplitude = 0.3, noise = 0.02))
        assertTrue(result is PitchResult.Pitch)
        assertTrue(abs(centsBetween((result as PitchResult.Pitch).frequencyHz, 110.0)) < 1.0)
    }
}
