package io.github.jlojkasek.ketape

import kotlin.math.roundToInt

internal val powerDurations = listOf(1, 5, 10, 15, 30, 60, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600)
internal val defaultPowerDurations = listOf(1, 5, 60, 300, 1200)
internal const val POWER_BEST_DURATIONS = "power_best_durations"

internal fun selectedPowerDurations(selection: Set<String>?): List<Int> =
    powerDurations.filter { it.toString() in selection.orEmpty() }.ifEmpty { defaultPowerDurations }

internal const val HEART_RECORD = 0
internal const val RECORD_PERIOD = "record_period"
internal const val RECORD_HEART = "record_heart"
internal const val RECORD_SOUND = "record_sound"

internal data class RideBest(val duration: Int, val value: Int)

internal enum class PowerRecordMark { RECENT, ALL_TIME }

internal fun powerRecordMark(
    watts: Int,
    recent: Int?,
    allTime: Int?,
    previous: PowerRecordMark?
): PowerRecordMark? = when {
    previous == PowerRecordMark.ALL_TIME || (allTime != null && watts > allTime) -> PowerRecordMark.ALL_TIME
    previous == PowerRecordMark.RECENT || (recent != null && watts > recent) -> PowerRecordMark.RECENT
    else -> null
}

internal class RideBestValues {
    private val samples = IntArray(powerDurations.last())
    private val sums = IntArray(powerDurations.size)
    private var count = 0
    private var previousSecond = Long.MIN_VALUE
    private var previousPowerSecond: Long? = null
    private var previousHeart: Int? = null

    fun reset() {
        count = 0
        previousSecond = Long.MIN_VALUE
        previousPowerSecond = null
        previousHeart = null
        sums.fill(0)
    }

    fun sample(second: Long, watts: Double?, bpm: Double?): List<RideBest> {
        if (second <= previousSecond) return emptyList()
        if (second != previousSecond + 1) previousHeart = null
        previousSecond = second
        val results = mutableListOf<RideBest>()
        if (watts != null && watts.isFinite() && watts in 0.0..5000.0) {
            // Keep the elapsed time when valid power resumes after an interruption.
            previousPowerSecond?.let { addZeros(second - it - 1, results) }
            addPower(watts.roundToInt(), results)
            previousPowerSecond = second
        }
        val heart = bpm?.takeIf { it.isFinite() && it in 30.0..230.0 }?.roundToInt()
        val last = previousHeart
        if (heart != null && last != null) results += RideBest(HEART_RECORD, minOf(heart, last))
        previousHeart = heart
        return results
    }

    fun samplePause(fromSecond: Long, throughSecond: Long): List<RideBest> {
        previousHeart = null
        val first = maxOf(fromSecond, previousSecond + 1)
        val lastPowerSecond = previousPowerSecond ?: return emptyList()
        if (first > throughSecond) return emptyList()

        val results = mutableListOf<RideBest>()
        addZeros(throughSecond - lastPowerSecond, results)
        previousSecond = throughSecond
        previousPowerSecond = throughSecond
        return results
    }

    private fun addZeros(seconds: Long, results: MutableList<RideBest>) {
        // Once the buffer contains only zeros, further zeros cannot improve any window.
        repeat(seconds.coerceAtMost(samples.size.toLong()).toInt()) { addPower(0, results) }
    }

    private fun addPower(value: Int, results: MutableList<RideBest>) {
        powerDurations.forEachIndexed { index, duration ->
            if (count >= duration) sums[index] -= samples[(count - duration) % samples.size]
            sums[index] += value
            if (count + 1 >= duration) {
                results += RideBest(duration, (sums[index].toDouble() / duration).roundToInt())
            }
        }
        samples[count % samples.size] = value
        count++
    }
}
