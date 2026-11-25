package de.jannes.stacked

import kotlin.math.roundToInt

internal val powerDurations = listOf(1, 5, 60, 300, 1200)
internal const val HEART_RECORD = 0
internal const val RECORD_PERIOD = "record_period"
internal const val RECORD_HEART = "record_heart"
internal const val RECORD_SOUND = "record_sound"

internal data class RideBest(val duration: Int, val value: Int)

internal class RideBestValues {
    private val samples = IntArray(powerDurations.last())
    private val sums = IntArray(powerDurations.size)
    private var count = 0
    private var previousSecond = Long.MIN_VALUE
    private var previousHeart: Int? = null

    fun reset() {
        count = 0
        previousSecond = Long.MIN_VALUE
        previousHeart = null
        sums.fill(0)
    }

    fun sample(second: Long, watts: Double?, bpm: Double?): List<RideBest> {
        if (second == previousSecond) return emptyList()
        if (second != previousSecond + 1) reset()
        previousSecond = second
        val results = mutableListOf<RideBest>()
        if (watts == null || !watts.isFinite() || watts !in 0.0..5000.0) {
            count = 0
            sums.fill(0)
        } else {
            val value = watts.roundToInt()
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
        val heart = bpm?.takeIf { it.isFinite() && it in 30.0..230.0 }?.roundToInt()
        val last = previousHeart
        if (heart != null && last != null) results += RideBest(HEART_RECORD, minOf(heart, last))
        previousHeart = heart
        return results
    }
}
