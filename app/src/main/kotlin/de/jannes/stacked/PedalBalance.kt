package de.jannes.stacked

import kotlin.math.abs

internal const val BALANCE_WARNING_KEY = "cadence_balance_warning"
internal val balanceWarningThresholds = listOf(0, 2, 3, 4, 5, 6, 8, 10)

internal fun exceedsBalanceThreshold(balance: Pair<Int, Int>?, threshold: Int): Boolean =
    balance != null && threshold > 0 && abs(balance.first - 50) > threshold

/** Karoo's left power share, in percent; calculate the complementary right share. */
internal fun pedalBalance(leftPercent: Double?): Pair<Int, Int>? {
    if (leftPercent == null || !leftPercent.isFinite() || leftPercent !in 0.0..100.0) return null
    val left = Math.round(leftPercent).toInt()
    return left to (100 - left)
}
