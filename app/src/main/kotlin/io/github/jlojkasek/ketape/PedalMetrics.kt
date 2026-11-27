package io.github.jlojkasek.ketape

import io.hammerhead.karooext.models.DataType
import kotlin.math.roundToInt

internal const val PEDAL_LAYOUT_KEY = "cadence_layout"
internal enum class PedalLayout(val key: String) {
    CLASSIC("classic"), PERFORMANCE("performance"), DYNAMICS("dynamics");
    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

private fun validPedalPercent(value: Double?): Double? = value
    ?.takeIf { it.isFinite() && it in 0.0..100.0 }

internal fun pedalPercent(value: Double?): Int? = validPedalPercent(value)?.let { Math.round(it).toInt() }

internal fun pedalPair(left: Double?, right: Double?): String =
    "${pedalPercent(left) ?: "–"}/${pedalPercent(right) ?: "–"}"

internal data class PedalMetrics(
    val balanceLeft: Double? = null,
    val teLeft: Double? = null, val teRight: Double? = null,
    val psLeft: Double? = null, val psRight: Double? = null
) {
    val balance get() = pedalBalance(balanceLeft)
    val balanceText get() = balance?.let { "${it.first}/${it.second}" } ?: "–/–"
    val performancePsText: String
        get() {
            val left = validPedalPercent(psLeft)
            val right = validPedalPercent(psRight)
            return when {
                left != null && right != null -> "PS ${((left + right) / 2).roundToInt()} %"
                left != null -> "PS L ${left.roundToInt()} %"
                right != null -> "PS R ${right.roundToInt()} %"
                else -> "PS – %"
            }
        }
    val teText get() = "TE % ${pedalPair(teLeft, teRight)}"
    val psText get() = "PS % ${pedalPair(psLeft, psRight)}"
}

/** Whole stream snapshots prevent mixing sides from different updates. Missing fields clear immediately. */
internal class PedalSamples {
    private val samples = mutableMapOf<String, Pair<Long, Map<String, Double>>>()
    @Synchronized fun update(type: String, values: Map<String, Double>?, now: Long) {
        if (values == null) samples.remove(type) else samples[type] = now to values.toMap()
    }
    @Synchronized fun snapshot(now: Long): PedalMetrics {
        fun value(type: String, field: String): Double? = samples[type]?.takeIf {
            now - it.first in 0L..5000L
        }?.second?.get(field)
        return PedalMetrics(
            value(DataType.Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE, DataType.Field.PEDAL_POWER_BALANCE_LEFT),
            value(DataType.Type.TORQUE_EFFECTIVENESS, DataType.Field.TORQUE_EFFECTIVENESS_LEFT),
            value(DataType.Type.TORQUE_EFFECTIVENESS, DataType.Field.TORQUE_EFFECTIVENESS_RIGHT),
            value(DataType.Type.PEDAL_SMOOTHNESS, DataType.Field.PEDAL_SMOOTHNESS_LEFT),
            value(DataType.Type.PEDAL_SMOOTHNESS, DataType.Field.PEDAL_SMOOTHNESS_RIGHT)
        )
    }
}

internal fun pedalSources(layout: PedalLayout, classicBalance: Boolean): Set<String> = buildSet {
    if (layout != PedalLayout.CLASSIC || classicBalance) add(DataType.Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE)
    if (layout != PedalLayout.CLASSIC) add(DataType.Type.PEDAL_SMOOTHNESS)
    if (layout == PedalLayout.DYNAMICS) {
        add(DataType.Type.TORQUE_EFFECTIVENESS)
    }
}
