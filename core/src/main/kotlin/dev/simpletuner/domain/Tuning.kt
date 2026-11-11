package dev.simpletuner.domain

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/** Equal temperament, A4 = 440 Hz. Frequencies are computed rather than rounded. */
data class TuningString(val number: Int, val note: String, val octave: Int, val midiNote: Int) {
    val frequencyHz: Double get() = 440.0 * 2.0.pow((midiNote - 69) / 12.0)
}

enum class InstrumentMode(val strings: List<TuningString>) {
    GUITAR(listOf(
        TuningString(6, "E", 2, 40), TuningString(5, "A", 2, 45),
        TuningString(4, "D", 3, 50), TuningString(3, "G", 3, 55),
        TuningString(2, "B", 3, 59), TuningString(1, "E", 4, 64),
    )),
    BASS(listOf(
        TuningString(4, "E", 1, 28), TuningString(3, "A", 1, 33),
        TuningString(2, "D", 2, 38), TuningString(1, "G", 2, 43),
    )),
}

fun centsBetween(measuredHz: Double, targetHz: Double): Double {
    require(measuredHz.isFinite() && measuredHz > 0)
    require(targetHz.isFinite() && targetHz > 0)
    return 1200.0 * (log2(measuredHz) - log2(targetHz))
}

data class StringMatch(val string: TuningString, val cents: Double)

/** Stateless candidate only. Confidence, hysteresis and silence belong to the tracking stage. */
fun nearestString(frequencyHz: Double, mode: InstrumentMode): StringMatch? {
    if (!frequencyHz.isFinite() || frequencyHz !in 35.0..400.0) return null
    return mode.strings.map { StringMatch(it, centsBetween(frequencyHz, it.frequencyHz)) }
        .minByOrNull { abs(it.cents) }
}
