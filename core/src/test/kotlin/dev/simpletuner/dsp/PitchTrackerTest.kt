package dev.simpletuner.dsp

import dev.simpletuner.domain.InstrumentMode
import dev.simpletuner.domain.TuningPrecision
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class PitchTrackerTest {
    private fun pitch(hz: Double, confidence: Double = 0.99) = PitchResult.Pitch(hz, confidence)

    @Test fun `standard mode recognizes wide deviations on guitar and bass`() {
        for (mode in InstrumentMode.entries) {
            val hz = mode.strings.last().frequencyHz * 2.0.pow(300.0 / 1200.0)
            val tracker = PitchTracker(TuningPrecision.STANDARD)
            tracker.update(pitch(hz), mode, 0)
            val tracking = tracker.update(pitch(hz), mode, 40) as TrackedPitch.Tracking
            assertEquals(mode.strings.last(), tracking.match.string)
            assertEquals(300.0, tracking.match.cents, 0.001)
            assertEquals(TrackedPitch.Uncertain, PitchTracker().update(pitch(hz), mode, 0))
        }
    }

    @Test fun `standard mode switches at the logarithmic midpoint between B3 and E4`() {
        val mode = InstrumentMode.GUITAR
        val (b3, e4) = mode.strings.takeLast(2)
        val below = b3.frequencyHz * 2.0.pow(249.0 / 1200.0)
        val above = b3.frequencyHz * 2.0.pow(251.0 / 1200.0)
        val tracker = PitchTracker(TuningPrecision.STANDARD)
        tracker.update(pitch(below), mode, 0)
        assertEquals(b3, (tracker.update(pitch(below), mode, 40) as TrackedPitch.Tracking).match.string)
        assertEquals(TrackedPitch.Uncertain, tracker.update(pitch(above), mode, 80))
        assertEquals(e4, (tracker.update(pitch(above), mode, 120) as TrackedPitch.Tracking).match.string)
    }

    @Test fun `standard mode accepts stable deviations up to two steps`() {
        for (cents in listOf(-21.0, -19.0, 19.0, 21.0)) {
            val tracker = PitchTracker(TuningPrecision.STANDARD)
            val signal = pitch(110.0 * 2.0.pow(cents / 1200.0))
            repeat(3) { tracker.update(signal, InstrumentMode.GUITAR, it * 40L) }
            val tracking = tracker.update(signal, InstrumentMode.GUITAR, 120) as TrackedPitch.Tracking
            assertEquals(kotlin.math.abs(cents) <= 20.0, tracking.stable)
        }
    }

    @Test fun `single outlier does not switch string and actual switch is confirmed`() {
        val tracker = PitchTracker()
        val mode = InstrumentMode.GUITAR
        assertEquals(TrackedPitch.Uncertain, tracker.update(pitch(110.0), mode, 0))
        val a = tracker.update(pitch(110.0), mode, 40) as TrackedPitch.Tracking
        assertEquals("A", a.match.string.note)
        assertEquals(TrackedPitch.Uncertain, tracker.update(pitch(146.8324), mode, 80))
        assertEquals("A", (tracker.update(pitch(110.0), mode, 120) as TrackedPitch.Tracking).match.string.note)
        assertEquals(TrackedPitch.Uncertain, tracker.update(pitch(146.8324), mode, 160))
        assertEquals("D", (tracker.update(pitch(146.8324), mode, 200) as TrackedPitch.Tracking).match.string.note)
    }

    @Test fun `tuned requires stable measurements and ends immediately on deviation`() {
        val tracker = PitchTracker()
        val mode = InstrumentMode.GUITAR
        repeat(3) { tracker.update(pitch(110.0), mode, it * 40L) }
        assertTrue((tracker.update(pitch(110.0), mode, 120) as TrackedPitch.Tracking).stable)
        val off = 110.0 * 2.0.pow(7.0 / 1200.0)
        assertFalse((tracker.update(pitch(off), mode, 160) as TrackedPitch.Tracking).stable)
        assertEquals(TrackedPitch.Uncertain, tracker.update(pitch(110.0, 0.7), mode, 200))
    }

    @Test fun `silence clears stale signal and reset allows mode change`() {
        val tracker = PitchTracker()
        tracker.update(pitch(110.0), InstrumentMode.GUITAR, 0)
        tracker.update(pitch(110.0), InstrumentMode.GUITAR, 40)
        assertEquals(TrackedPitch.Listening, tracker.update(PitchResult.Silence, InstrumentMode.GUITAR, 400))
        assertEquals(TrackedPitch.Uncertain, tracker.update(pitch(55.0), InstrumentMode.BASS, 440))
        assertEquals("A", (tracker.update(pitch(55.0), InstrumentMode.BASS, 480) as TrackedPitch.Tracking).match.string.note)
    }
}
