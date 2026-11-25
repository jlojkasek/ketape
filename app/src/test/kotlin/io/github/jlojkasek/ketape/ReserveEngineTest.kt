package io.github.jlojkasek.ketape

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln

class ReserveEngineTest {
    private val parameters = ReserveParameters(300.0, 22_000.0, "Manual CP")
    private val engine = ReserveEngine(parameters)
    private var time = 0.0

    private fun ride(watts: Double, seconds: Int, interval: Double = 1.0) {
        engine.sample(time, watts)
        repeat((seconds / interval).toInt()) {
            time += interval
            engine.sample(time, watts)
        }
    }

    private fun snapshot() = engine.snapshot(time, 10, false)

    @Test fun depletionMatchesReferenceAndIsIndependentOfSamplingInterval() {
        ride(400.0, 120, .5)
        assertEquals(10_000.0, engine.totals.balance, 1e-7)
        assertEquals(45.454545, snapshot().percent!!, 1e-5)
        assertEquals(16_000.0, engine.totals.integral / engine.totals.elapsed, 1e-7)
        assertEquals(12_000.0, engine.totals.expended, 1e-7)
        assertEquals(120.0, engine.totals.above, 1e-7)
        assertEquals(100.0, snapshot().forecast!!, 1e-7)
        assertEquals(466.6667, snapshot().available(60)!!, .0001)
        assertEquals(44.0, engine.totals.levels[0], 1e-7)
        assertEquals(66.0, engine.totals.levels[1], 1e-7)
        assertEquals(10.0, engine.totals.levels[2], 1e-7)
    }

    @Test fun fullAndAtThresholdStayStable() {
        ride(100.0, 10)
        assertEquals(ReserveStatus.FULL, snapshot().status)
        assertEquals(22_000.0, engine.totals.balance, 0.0)
        engine.gap()
        ride(300.0, 10)
        assertEquals(22_000.0, engine.totals.balance, 0.0)
        assertNull(snapshot().forecast)
        assertEquals(20.0, engine.totals.levels.first(), 1e-8)
    }

    @Test fun genuineZeroRecoversButGapAndInvalidDataDoNot() {
        ride(400.0, 120)
        engine.sample(time + 1, null)
        engine.sample(time + 2, Double.NaN)
        engine.sample(time + 3, -1.0)
        time += 30
        assertEquals(ReserveStatus.MISSING, snapshot().status)
        assertNull(snapshot().forecast)
        assertEquals(120.0, engine.totals.elapsed, 0.0)
        ride(0.0, 60, 2.0)
        val balance = 22_000 - 12_000 * exp(-300.0 * 60 / 22_000)
        assertEquals(balance, engine.totals.balance, 1e-7)
        assertEquals(22_000.0 / 300 * ln((22_000 - balance) / 2_200), snapshot().forecast!!, 1e-7)
        assertEquals(180.0, engine.totals.levels.sum(), 1e-8)
        assertEquals(10_000.0, engine.totals.minimum, 1e-7)
    }

    @Test fun negativeReserveRemainsVisibleAndRecoversWithSameModel() {
        ride(500.0, 110)
        assertEquals(0.0, engine.totals.balance, 0.0)
        assertNull(snapshot().available(60))
        ride(500.0, 5)
        assertEquals(-1_000.0, engine.totals.balance, 1e-8)
        assertEquals(0.0, snapshot().forecast!!, 0.0)
        assertNull(snapshot().available(60))
        engine.gap()
        ride(100.0, 600)
        assertEquals(22_000 - 23_000 * exp(-200.0 * 600 / 22_000), engine.totals.balance, 1e-7)
        assertEquals(ReserveStatus.FULL, snapshot().status)
        assertNull(snapshot().forecast)
        assertEquals(23_000.0, engine.totals.expended, 1e-7)
        assertTrue(engine.totals.balance in 21_000.0..22_000.0)
    }

    @Test fun recoveryForecastReturnsZeroOnceNinetyPercentIsReached() {
        ride(400.0, 120)
        engine.gap()
        ride(100.0, 220)
        assertEquals(ReserveStatus.RECOVERING, snapshot().status)
        assertEquals(0.0, snapshot().forecast!!, 0.0)
    }

    @Test fun restoredTotalsContinueWithoutCountingThePauseOrDowntime() {
        ride(450.0, 30)
        val restored = ReserveEngine(parameters, engine.totals, restored = true)
        restored.sample(120.0, 450.0)
        restored.sample(121.0, 450.0)
        assertEquals(31.0, restored.totals.elapsed, 0.0)
        assertEquals(17_350.0, restored.totals.balance, 0.0)
        restored.gap()
        val paused = restored.snapshot(125.0, 10, true)
        assertTrue(paused.restored)
        assertEquals(ReserveStatus.PAUSED, paused.status)
        assertNull(paused.forecast)
    }

    @Test fun variableIntervalsUseActualDurationAndDetachAcrossDropout() {
        engine.sample(0.0, 400.0)
        engine.sample(.3, 400.0)
        engine.sample(1.5, 400.0)
        engine.sample(2.0, 400.0)
        engine.sample(8.0, 0.0)
        assertEquals(21_800.0, engine.totals.balance, 1e-8)
        assertEquals(2.0, engine.totals.elapsed, 1e-8)
        assertEquals(2, engine.snapshot(8.0, 10, false).history.map { it.segment }.distinct().size)
    }

    @Test fun rejectsImplausibleOrMissingParameters() {
        assertFalse(ReserveParameters(Double.NaN, 22_000.0, "FTP").valid)
        assertFalse(ReserveParameters(300.0, 22.0, "FTP").valid)
        assertTrue(parameters.valid)
    }
}
