package io.github.jlojkasek.ketape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldSmoothingTest {
    @Test fun pulseWindowExpiresAndDoesNotWeightRepeatedUpdates() {
        val smoothing = PulseSmoother(3)
        assertEquals(120.0, smoothing.update(0, 120.0))
        assertEquals(130.0, smoothing.update(1000, 140.0))
        assertEquals(140.0, smoothing.update(1100, 160.0))
        assertEquals(160.0, smoothing.update(2000, 200.0))
        assertEquals(180.0, smoothing.update(3000, 180.0))
    }

    @Test fun missingPulseResetsTheWindow() {
        val smoothing = PulseSmoother(5)
        smoothing.update(0, 120.0)
        assertNull(smoothing.update(1000, null))
        assertEquals(180.0, smoothing.update(2000, 180.0))
        assertEquals(160.0, smoothing.update(6000, 160.0))
    }
}
