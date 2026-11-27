package io.github.jlojkasek.ketape

import org.junit.Assert.*
import org.junit.Test

class RideBestValuesTest {
    @Test fun tableRecordMarksRequireAnImprovementAndAllTimeTakesPriority() {
        assertNull(powerRecordMark(430, recent = 430, allTime = 500, previous = null))
        assertNull(powerRecordMark(550, recent = null, allTime = null, previous = null))
        assertEquals(PowerRecordMark.RECENT, powerRecordMark(480, 430, 500, null))
        assertEquals(PowerRecordMark.ALL_TIME, powerRecordMark(510, 430, 500, PowerRecordMark.RECENT))
        assertEquals(PowerRecordMark.ALL_TIME, powerRecordMark(520, null, null, PowerRecordMark.ALL_TIME))
        assertNull(powerRecordMark(520, 520, 520, null))
    }

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

    @Test fun longWindowsSurviveShortGapsWithoutInflatingPower() {
        val recorder = RideBestValues()
        for (second in 0L..4500L) {
            val missing = second % 600 in 100L..102L
            // Some gaps have no ticks at all, others have ticks without sensor data.
            if (missing && second < 1200) continue
            val values = recorder.sample(second, 250.0.takeUnless { missing }, null)
            when (second) {
                2698L -> assertFalse(values.any { it.duration == 2700 })
                2699L -> assertTrue(RideBest(2700, 249) in values)
                3598L -> assertFalse(values.any { it.duration == 3600 })
                3599L, 4500L -> assertTrue(RideBest(3600, 249) in values)
            }
        }
    }

    @Test fun longSensorGapsDoNotEraseElapsedTime() {
        val recorder = RideBestValues()
        for (second in 0L..3599L) {
            val missing = second % 1200 in 600L..608L
            val values = recorder.sample(second, 250.0.takeUnless { missing }, null)
            when (second) {
                2698L -> assertFalse(values.any { it.duration == 2700 })
                2699L -> assertTrue(RideBest(2700, 248) in values)
                3598L -> assertFalse(values.any { it.duration == 3600 })
                3599L -> assertTrue(RideBest(3600, 248) in values)
            }
        }
    }

    @Test fun newRidesRequireFreshCompleteWindows() {
        val recorder = RideBestValues()
        for (second in 0L..3L) recorder.sample(second, 500.0, null)
        assertTrue(recorder.sample(3, 900.0, null).isEmpty())
        recorder.reset()
        assertEquals(listOf(RideBest(1, 500)), recorder.sample(4, 500.0, null))
    }

    @Test fun fortyFiveAndSixtyMinuteWindowsIncludePauseAtZeroWatts() {
        val recorder = RideBestValues()
        for (second in 0L..1199L) recorder.sample(second, 250.0, null)
        recorder.samplePause(1200, 1210)
        // The recording loop can miss ticks while the Karoo is paused.
        recorder.samplePause(1200, 1319)
        for (second in 1320L..3599L) {
            val values = recorder.sample(second, 250.0, null)
            when (second) {
                2698L -> assertFalse(values.any { it.duration == 2700 })
                2699L -> assertTrue(RideBest(2700, 239) in values)
                3598L -> assertFalse(values.any { it.duration == 3600 })
                3599L -> assertTrue(RideBest(3600, 242) in values)
            }
        }
    }

    @Test fun bestCanCompleteWhilePausedAndLongPauseRollsOutOldPower() {
        val recorder = RideBestValues()
        for (second in 0L..3L) recorder.sample(second, 250.0, 120.0)
        assertTrue(RideBest(5, 200) in recorder.samplePause(4, 4))
        assertTrue(recorder.samplePause(4, 4).isEmpty())
        val paused = recorder.samplePause(4, 5000)
        assertTrue(paused.none { it.duration == HEART_RECORD })
        assertTrue(RideBest(3600, 0) in paused)
        val resumed = recorder.sample(5001, 300.0, 125.0)
        assertTrue(RideBest(5, 60) in resumed)
        assertTrue(resumed.none { it.duration == HEART_RECORD })
    }

    @Test fun pauseBeforeAnyValidPowerDoesNotCreateBests() {
        val recorder = RideBestValues()
        assertTrue(recorder.samplePause(0, 60).isEmpty())
        assertEquals(listOf(RideBest(1, 200)), recorder.sample(61, 200.0, null))
    }

    @Test fun evenAPauseWithoutAFullSecondBreaksTheHeartRatePair() {
        val recorder = RideBestValues()
        recorder.sample(0, 200.0, 180.0)
        assertTrue(recorder.samplePause(1, 0).isEmpty())
        assertEquals(listOf(RideBest(1, 200)), recorder.sample(1, 200.0, 185.0))
    }

    @Test fun shortGapCanCompleteAWindowOncePowerResumes() {
        val recorder = RideBestValues()
        for (second in 0L..3L) recorder.sample(second, 500.0, null)
        assertTrue(recorder.sample(4, null, null).isEmpty())
        val fiveSecondValues = recorder.sample(5, 100.0, null).filter { it.duration == 5 }
        assertEquals(listOf(RideBest(5, 400), RideBest(5, 320)), fiveSecondValues)
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
