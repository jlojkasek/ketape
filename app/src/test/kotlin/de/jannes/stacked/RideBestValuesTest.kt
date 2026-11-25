package de.jannes.stacked

import org.junit.Assert.*
import org.junit.Test

class RideBestValuesTest {
    @Test fun peakAndRollingWindowsIncludeCoasting() {
        val recorder = RideBestValues()
        assertEquals(listOf(RideBest(1, 800)), recorder.sample(0, 800.0, null))
        for (second in 1L..3L) recorder.sample(second, 400.0, null)
        assertTrue(RideBest(5, 400) in recorder.sample(4, 0.0, null))
        assertTrue(RideBest(5, 240) in recorder.sample(5, 0.0, null))
    }

    @Test fun twentyMinuteWindowSurvivesBufferWrap() {
        val recorder = RideBestValues()
        for (second in 0L..1198L) {
            assertFalse(recorder.sample(second, 1200.0, null).any { it.duration == 1200 })
        }
        assertTrue(RideBest(1200, 1200) in recorder.sample(1199, 1200.0, null))
        assertTrue(RideBest(1200, 1199) in recorder.sample(1200, 0.0, null))
        assertTrue(RideBest(1200, 1200) in recorder.sample(1201, 2400.0, null))
    }

    @Test fun hourlyBestRequiresAFullWindowAndRollsForward() {
        val recorder = RideBestValues()
        for (second in 0L..3598L) {
            assertFalse(recorder.sample(second, 3600.0, null).any { it.duration == 3600 })
        }
        assertTrue(RideBest(3600, 3600) in recorder.sample(3599, 3600.0, null))
        assertTrue(RideBest(3600, 3599) in recorder.sample(3600, 0.0, null))
    }

    @Test fun gapsAndPausesRequireFreshCompleteWindows() {
        val recorder = RideBestValues()
        for (second in 0L..3L) recorder.sample(second, 500.0, null)
        assertEquals(listOf(RideBest(1, 500)), recorder.sample(5, 500.0, null))
        assertTrue(recorder.sample(5, 900.0, null).isEmpty())
        recorder.reset()
        assertEquals(listOf(RideBest(1, 500)), recorder.sample(6, 500.0, null))
    }

    @Test fun heartRateRequiresTwoConsecutiveReadingsAboveThreshold() {
        val recorder = RideBestValues()
        assertTrue(recorder.sample(0, null, 220.0).isEmpty())
        assertEquals(listOf(RideBest(HEART_RECORD, 180)), recorder.sample(1, null, 180.0))
        assertTrue(recorder.sample(2, null, null).isEmpty())
        assertTrue(recorder.sample(3, null, 191.0).isEmpty())
        assertEquals(listOf(RideBest(HEART_RECORD, 190)), recorder.sample(4, null, 190.0))
    }
}
