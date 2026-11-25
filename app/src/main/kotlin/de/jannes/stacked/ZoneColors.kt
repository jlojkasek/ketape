package de.jannes.stacked

internal data class ZoneRange(val min: Int, val max: Int)
internal data class ZoneStyle(val number: Int, val background: Int, val foreground: Int)

internal object ZoneColors {
    // Karoo-inspired palette. The SDK exposes zone limits, but no native color tokens.
    private val power = listOf(0xFF9E9E9E, 0xFF2196F3, 0xFF4CAF50, 0xFFFFEB3B,
        0xFFFF9800, 0xFFF44336, 0xFF9C27B0).map { it.toInt() }
    private val heart = listOf(0xFF9E9E9E, 0xFF2196F3, 0xFF4CAF50, 0xFFFFEB3B,
        0xFFF44336).map { it.toInt() }

    fun resolve(value: Double?, zones: List<ZoneRange>, isPower: Boolean,
        systemPalette: List<Int>? = null): ZoneStyle? {
        if (value == null || !value.isFinite() || value < 0 || zones.isEmpty()) return null
        // Keep Karoo's ordering and numbering. Reject malformed profiles rather than invent limits.
        if (zones.any { it.min < 0 || it.max < it.min } ||
            zones.zipWithNext().any { (a, b) -> b.min <= a.min || b.min < a.max }) return null
        // At a shared boundary, the higher zone wins. Values outside the supplied ranges stay neutral.
        // Streams can contain fractional smoothed watts; classify the displayed integer.
        val displayedValue = Math.rint(value)
        val index = zones.indexOfLast { displayedValue >= it.min && displayedValue <= it.max }
        val palette = systemPalette ?: if (isPower) power else heart
        if (index !in palette.indices) return null
        val background = palette[index]
        val rgb = listOf((background shr 16) and 255, (background shr 8) and 255, background and 255)
            .map { channel ->
                val c = channel / 255.0
                if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
        val luminance = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
        val foreground = if ((luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05))
            0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        return ZoneStyle(index + 1, background, foreground)
    }
}
