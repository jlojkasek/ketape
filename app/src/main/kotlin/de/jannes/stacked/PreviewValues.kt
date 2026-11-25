package de.jannes.stacked

/** Deterministic editor-only examples. Each step lasts two render ticks. */
internal object PreviewValues {
    data class Frame(val main: Double, val average: Double, val secondary: Double, val balanceLeft: Double = 48.0)
    fun frame(field: String, tick: Int): Frame {
        val frames = when {
            field.startsWith("power-") -> listOf(
                Frame(99.0, 118.0, 126.0), Frame(100.0, 121.0, 129.0),
                Frame(243.0, 218.0, 232.0), Frame(315.0, 221.0, 237.0),
                Frame(999.0, 225.0, 249.0), Frame(1000.0, 228.0, 255.0))
            field == "heart" -> listOf(
                Frame(99.0, 105.0, 124.0), Frame(100.0, 106.0, 124.0),
                Frame(142.0, 136.0, 174.0), Frame(165.0, 139.0, 174.0),
                Frame(181.0, 142.0, 181.0), Frame(108.0, 138.0, 181.0))
            field == "speed" -> listOf(
                Frame(9.9 / 3.6, 8.5 / 3.6, 24.0 / 3.6), Frame(10.0 / 3.6, 8.6 / 3.6, 24.0 / 3.6),
                Frame(31.3 / 3.6, 28.1 / 3.6, 54.4 / 3.6), Frame(54.4 / 3.6, 28.4 / 3.6, 54.4 / 3.6),
                Frame(99.9 / 3.6, 28.8 / 3.6, 99.9 / 3.6), Frame(100.0 / 3.6, 29.0 / 3.6, 100.0 / 3.6))
            field == "distance" -> listOf(
                Frame(9900.0, 99.0, 86.0), Frame(10000.0, 100.0, 89.0),
                Frame(99900.0, 999.0, 865.0), Frame(100000.0, 1000.0, 890.0),
                Frame(312000.0, 9999.0, 9845.0), Frame(313000.0, 10000.0, 9900.0))
            else -> listOf(
                Frame(89.0, 83.0, 98.0, 48.0), Frame(99.0, 84.0, 99.0, 49.0),
                Frame(100.0, 85.0, 100.0, 50.0), Frame(110.0, 86.0, 110.0, 51.0),
                Frame(76.0, 84.0, 112.0, 52.0), Frame(88.0, 83.0, 112.0, 47.0))
        }
        return frames[Math.floorMod(tick / 2, frames.size)]
    }
}
