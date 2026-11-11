package dev.simpletuner.domain

import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class TuningTest {
    @Test fun `all ten reference frequencies follow A440 and match their string`() {
        val references = mapOf(
            InstrumentMode.GUITAR to listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276),
            InstrumentMode.BASS to listOf(41.2034, 55.0, 73.4162, 97.9989),
        )
        references.forEach { (mode, frequencies) ->
            mode.strings.zip(frequencies).forEach { (string, frequency) ->
                assertEquals(frequency, string.frequencyHz, 0.0001)
                assertEquals(string, nearestString(frequency, mode)?.string)
            }
        }
    }

    @Test fun `cents preserve sign and actual large deviation`() {
        for (cents in listOf(-700.0, -12.0, 0.0, 7.0, 700.0)) {
            assertEquals(cents, centsBetween(110.0 * 2.0.pow(cents / 1200.0), 110.0), 1e-9)
        }
    }

    @Test fun `octave is twelve hundred cents`() {
        assertEquals(1200.0, centsBetween(220.0, 110.0), 1e-9)
    }

    @Test fun `mode changes the candidate set`() {
        assertEquals(InstrumentMode.GUITAR.strings.first(), nearestString(55.0, InstrumentMode.GUITAR)?.string)
        assertEquals(InstrumentMode.BASS.strings[1], nearestString(55.0, InstrumentMode.BASS)?.string)
    }

    @Test fun `invalid and out of range measurements have no candidate`() {
        for (frequency in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 34.9, 400.1)) {
            assertNull(nearestString(frequency, InstrumentMode.GUITAR))
            assertNull(nearestString(frequency, InstrumentMode.BASS))
        }
    }

    @Test fun `analysis range includes both boundaries`() {
        assertNotNull(nearestString(35.0, InstrumentMode.BASS))
        assertNotNull(nearestString(400.0, InstrumentMode.GUITAR))
    }

    @Test fun `invalid cents inputs are rejected`() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            assertThrows(IllegalArgumentException::class.java) { centsBetween(invalid, 110.0) }
            assertThrows(IllegalArgumentException::class.java) { centsBetween(110.0, invalid) }
        }
    }
}
