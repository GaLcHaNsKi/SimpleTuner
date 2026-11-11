package dev.simpletuner.dsp

import dev.simpletuner.domain.InstrumentMode
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class PitchTrackerTest {
    private fun pitch(hz: Double, confidence: Double = 0.99) = PitchResult.Pitch(hz, confidence)

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
