package de.jannes.stacked

import org.junit.Assert.*
import org.junit.Test

class PreviewValuesTest {
    private val fields = listOf("power-1s", "power-3s", "power-5s", "heart", "speed", "distance", "cadence")

    @Test fun everyFieldChangesAllThreeValuesAndLoops() {
        fields.forEach { field ->
            val frames = (0 until 12).map { PreviewValues.frame(field, it) }
            assertTrue(field, frames.map { it.main }.distinct().size > 1)
            assertTrue(field, frames.map { it.average }.distinct().size > 1)
            assertTrue(field, frames.map { it.secondary }.distinct().size > 1)
            assertEquals(PreviewValues.frame(field, 0), PreviewValues.frame(field, 12))
        }
    }
    @Test fun valuesAreFiniteAndNonnegative() {
        fields.forEach { field ->
            (0 until 12).forEach { tick ->
                val f = PreviewValues.frame(field, tick)
                listOf(f.main, f.average, f.secondary).forEach { assertTrue(it.isFinite() && it >= 0.0) }
                assertTrue(f.balanceLeft in 0.0..100.0)
            }
        }
    }
    @Test fun previewExercisesDigitBoundariesAndBalance() {
        val power = (0 until 12).map { PreviewValues.frame("power-1s", it).main }
        assertTrue(power.containsAll(listOf(99.0, 100.0, 999.0, 1000.0)))
        val cadence = (0 until 12).map { PreviewValues.frame("cadence", it) }
        assertTrue(cadence.map { it.balanceLeft }.distinct().size > 1)
        assertTrue(cadence.any { it.main == 99.0 } && cadence.any { it.main == 100.0 })
    }
}
