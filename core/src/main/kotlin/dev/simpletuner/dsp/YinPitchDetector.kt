package dev.simpletuner.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

sealed interface PitchResult {
    data object Silence : PitchResult
    data object Uncertain : PitchResult
    data class Pitch(val frequencyHz: Double, val confidence: Double) : PitchResult
}

/** Single-threaded YIN detector. Buffers are allocated once and reused between windows. */
class YinPitchDetector(private val sampleRate: Int, private val windowSize: Int = 8192) {
    private val decimation = 4
    private val reducedSize = windowSize / decimation
    private val effectiveRate = sampleRate.toDouble() / decimation
    private val samples = DoubleArray(reducedSize)
    private val difference = DoubleArray(reducedSize / 2 + 1)
    private val minLag = floor(effectiveRate / 1000.0).toInt().coerceAtLeast(2)
    private val maxLag = ceil(effectiveRate / 25.0).toInt().coerceAtMost(reducedSize / 2 - 1)

    init {
        require(sampleRate >= 16000 && windowSize % decimation == 0 && maxLag > minLag)
    }

    fun detect(pcm: ShortArray): PitchResult {
        require(pcm.size == windowSize)
        var mean = 0.0
        for (i in samples.indices) {
            var sum = 0.0
            for (j in 0 until decimation) sum += pcm[i * decimation + j].toDouble()
            samples[i] = sum / (decimation * 32768.0)
            mean += samples[i]
        }
        mean /= reducedSize
        var energy = 0.0
        for (i in samples.indices) {
            samples[i] -= mean
            energy += samples[i] * samples[i]
        }
        if (sqrt(energy / reducedSize) < 0.003) return PitchResult.Silence

        // A fixed comparison length avoids bias towards long lags.
        val comparisonSize = reducedSize - maxLag - 1
        difference[0] = 1.0
        var cumulative = 0.0
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until comparisonSize) {
                val delta = samples[i] - samples[i + lag]
                sum += delta * delta
            }
            cumulative += sum
            difference[lag] = if (cumulative > 0.0) sum * lag / cumulative else 1.0
        }
        var lag = minLag
        while (lag < maxLag) {
            if (difference[lag] < 0.08) {
                while (lag + 1 <= maxLag && difference[lag + 1] < difference[lag]) lag++
                val left = difference[lag - 1]
                val center = difference[lag]
                val right = difference[lag + 1]
                val denominator = 2.0 * (2.0 * center - right - left)
                val correction = if (denominator == 0.0) 0.0 else (right - left) / denominator
                val frequency = effectiveRate / (lag + correction.coerceIn(-1.0, 1.0))
                val confidence = (1.0 - center).coerceIn(0.0, 1.0)
                return if (frequency in 35.0..400.0) PitchResult.Pitch(frequency, confidence)
                else PitchResult.Uncertain
            }
            lag++
        }
        return PitchResult.Uncertain
    }
}
