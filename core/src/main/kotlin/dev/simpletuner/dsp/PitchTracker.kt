package dev.simpletuner.dsp

import dev.simpletuner.domain.InstrumentMode
import dev.simpletuner.domain.StringMatch
import dev.simpletuner.domain.TuningPrecision
import dev.simpletuner.domain.centsBetween
import dev.simpletuner.domain.nearestString
import kotlin.math.abs

sealed interface TrackedPitch {
    data object Listening : TrackedPitch
    data object Uncertain : TrackedPitch
    data class Tracking(val frequencyHz: Double, val match: StringMatch, val stable: Boolean) : TrackedPitch
}

/** Debounces candidate changes independently of the UI animation. */
class PitchTracker(private val precision: TuningPrecision = TuningPrecision.PRECISE) {
    private var current: StringMatch? = null
    private var pendingNumber: Int? = null
    private var pendingCount = 0
    private val recent = ArrayDeque<Double>()
    private var validCount = 0
    private var lastValidMs: Long? = null

    fun reset() {
        current = null
        pendingNumber = null
        pendingCount = 0
        recent.clear()
        validCount = 0
        lastValidMs = null
    }

    fun update(result: PitchResult, mode: InstrumentMode, nowMs: Long): TrackedPitch {
        if (result !is PitchResult.Pitch || result.confidence < 0.92) {
            pendingNumber = null
            pendingCount = 0
            validCount = 0
            if (lastValidMs?.let { nowMs - it >= 250 } != false) reset()
            return if (result is PitchResult.Silence) TrackedPitch.Listening else TrackedPitch.Uncertain
        }
        val candidate = nearestString(result.frequencyHz, mode)
        if (candidate == null || abs(candidate.cents) > precision.maxMatchCents) {
            pendingNumber = null
            pendingCount = 0
            validCount = 0
            if (lastValidMs?.let { nowMs - it >= 250 } != false) reset()
            return TrackedPitch.Uncertain
        }
        lastValidMs = nowMs
        val previous = current
        if (previous == null || previous.string != candidate.string) {
            val oldDistance = previous?.let { abs(centsBetween(result.frequencyHz, it.string.frequencyHz)) }
            if (precision.switchMarginCents > 0 && oldDistance != null &&
                abs(candidate.cents) + precision.switchMarginCents >= oldDistance
            ) {
                return TrackedPitch.Uncertain
            }
            if (pendingNumber == candidate.string.number) pendingCount++ else {
                pendingNumber = candidate.string.number
                pendingCount = 1
            }
            if (pendingCount < 2) return TrackedPitch.Uncertain
            current = candidate
            pendingNumber = null
            pendingCount = 0
            recent.clear()
            validCount = 0
        } else {
            pendingNumber = null
            pendingCount = 0
        }
        if (recent.size == precision.medianSize) recent.removeFirst()
        recent.addLast(result.frequencyHz)
        validCount++
        val median = recent.sorted()[recent.size / 2]
        val match = StringMatch(candidate.string, centsBetween(median, candidate.string.frequencyHz))
        current = match
        // Require the latest raw sample as well, so stale median cannot claim tuning.
        val stable = validCount >= 3 && abs(match.cents) <= precision.tunedCents &&
            abs(candidate.cents) <= precision.tunedCents
        return TrackedPitch.Tracking(median, match, stable)
    }
}
