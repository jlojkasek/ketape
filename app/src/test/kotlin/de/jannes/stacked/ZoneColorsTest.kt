package de.jannes.stacked

import org.junit.Assert.*
import org.junit.Test

class ZoneColorsTest {
    private val zones = listOf(ZoneRange(0, 150), ZoneRange(150, 225), ZoneRange(226, 300))

    @Test fun sharedBoundaryUsesHigherZone() {
        assertEquals(1, ZoneColors.resolve(149.4, zones, true)?.number)
        assertEquals(2, ZoneColors.resolve(150.0, zones, true)?.number)
        assertEquals(3, ZoneColors.resolve(226.0, zones, true)?.number)
    }
    @Test fun invalidAndMissingValuesStayNeutral() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 301.0).forEach {
            assertNull(ZoneColors.resolve(it, zones, true))
        }
        assertNull(ZoneColors.resolve(100.0, emptyList(), true))
        assertNull(ZoneColors.resolve(200.0, listOf(ZoneRange(0, 150), ZoneRange(250, 300)), true))
        assertNull(ZoneColors.resolve(100.0, listOf(ZoneRange(200, 100)), true))
        assertNull(ZoneColors.resolve(100.0, zones.reversed(), true))
        assertNull(ZoneColors.resolve(100.0, listOf(ZoneRange(0, 150), ZoneRange(140, 200)), true))
    }
    @Test fun zeroPowerIsValid() { assertEquals(1, ZoneColors.resolve(0.0, zones, true)?.number) }
    @Test fun fractionalPowerUsesDisplayedInteger() {
        assertEquals(2, ZoneColors.resolve(225.4, zones, true)?.number)
        assertEquals(3, ZoneColors.resolve(225.6, zones, true)?.number)
    }
    @Test fun profileUpdatesChangeClassification() {
        assertEquals(2, ZoneColors.resolve(200.0, zones, true)?.number)
        assertEquals(1, ZoneColors.resolve(200.0, listOf(ZoneRange(0, 250)), true)?.number)
    }
    @Test fun colorsHaveReadableTextContrast() {
        val ranges = (0..6).map { ZoneRange(it * 100, it * 100 + 99) }
        for (power in listOf(true, false)) {
            val count = if (power) 7 else 5
            for (i in 0 until count) {
                val style = requireNotNull(ZoneColors.resolve(i * 100.0, ranges.take(count), power))
                assertEquals(i + 1, style.number)
                fun luminance(color: Int): Double {
                    val channels = listOf((color shr 16) and 255, (color shr 8) and 255, color and 255)
                        .map { val c = it / 255.0; if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
                    return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
                }
                val a = luminance(style.background); val b = luminance(style.foreground)
                assertTrue((maxOf(a, b) + .05) / (minOf(a, b) + .05) >= 4.5)
            }
        }
    }
}
