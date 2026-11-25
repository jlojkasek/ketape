package de.jannes.stacked

/** Karoo's left power share, in percent; calculate the complementary right share. */
internal fun pedalBalance(leftPercent: Double?): Pair<Int, Int>? {
    if (leftPercent == null || !leftPercent.isFinite() || leftPercent !in 0.0..100.0) return null
    val left = Math.round(leftPercent).toInt()
    return left to (100 - left)
}
