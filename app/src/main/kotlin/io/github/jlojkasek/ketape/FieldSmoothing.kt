package io.github.jlojkasek.ketape

import io.hammerhead.karooext.models.DataType.Type

internal val smoothingOptions = listOf(1, 3, 5, 10)

internal fun smoothingKey(field: String) = "smoothing_$field"

internal fun smoothedSource(field: String, seconds: Int, original: String): String = when (field) {
    "power" -> when (seconds) {
        3 -> Type.SMOOTHED_3S_AVERAGE_POWER
        5 -> Type.SMOOTHED_5S_AVERAGE_POWER
        10 -> Type.SMOOTHED_10S_AVERAGE_POWER
        else -> original
    }
    "cadence" -> when (seconds) {
        3 -> Type.SMOOTHED_3S_AVERAGE_CADENCE
        5 -> Type.SMOOTHED_5S_AVERAGE_CADENCE
        10 -> Type.SMOOTHED_10S_AVERAGE_CADENCE
        else -> original
    }
    "speed" -> when (seconds) {
        3 -> Type.SMOOTHED_3S_AVERAGE_SPEED
        5 -> Type.SMOOTHED_5S_AVERAGE_SPEED
        10 -> Type.SMOOTHED_10S_AVERAGE_SPEED
        else -> original
    }
    else -> original
}

internal class PulseSmoother(private val seconds: Int) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    @Synchronized
    fun update(nowMs: Long, value: Double?): Double? {
        if (value == null || !value.isFinite() || value <= 0.0) {
            samples.clear()
            return null
        }
        if (seconds <= 1) return value

        val second = nowMs / 1000
        val last = samples.lastOrNull()?.first
        if (last != null && second - last > 2) samples.clear()
        if (samples.lastOrNull()?.first == second) samples.removeLast()
        samples.addLast(second to value)
        while (samples.first().first <= second - seconds) samples.removeFirst()
        return samples.map { it.second }.average()
    }
}
