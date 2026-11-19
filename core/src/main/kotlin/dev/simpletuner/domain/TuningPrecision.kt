package dev.simpletuner.domain

enum class TuningPrecision(
    val maxMatchCents: Double,
    val tunedCents: Double,
    val centsPerStep: Int,
    val scaleCents: Int,
    internal val medianSize: Int,
    internal val switchMarginCents: Double,
) {
    STANDARD(400.0, 20.0, 10, 400, 5, 0.0),
    PRECISE(150.0, 3.0, 1, 50, 3, 20.0),
}
