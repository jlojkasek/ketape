package io.github.jlojkasek.ketape

import android.content.Context
import io.hammerhead.karooext.models.RideState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RideLifecycleTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val settings get() = ReservePreferences(context)
    private var now = 10_000L

    @Before fun configure() {
        settings.prefs.edit().clear().putBoolean("reserve_manual", true)
            .putFloat("reserve_cp", 300f).putFloat("reserve_kj", 22f).commit()
    }

    @Test fun palmaresMarksOnlyInterruptedRidesAndKeepsWarningAcrossBothPauseTypes() {
        val continuity = RideContinuity()
        continuity.observe(RideState.Recording)
        assertTrue(continuity.incomplete)
        for (state in listOf(RideState.Paused(true), RideState.Recording, RideState.Paused(false))) {
            continuity.observe(state)
            assertTrue(continuity.incomplete)
        }
        continuity.observe(RideState.Idle)
        continuity.observe(RideState.Recording)
        assertFalse(continuity.incomplete)
        val pausedStartup = RideContinuity()
        pausedStartup.observe(RideState.Paused(false))
        assertTrue(pausedStartup.incomplete)
    }

    @Test fun pauseResumeSensorChangeAndNewRideHaveIndependentAccounting() {
        val monitor = ReserveMonitor(context) { now }
        try {
            monitor.onState(RideState.Idle)
            monitor.onState(RideState.Recording)
            monitor.sample(now, 400.0, "sensor-a")
            now += 1000
            monitor.sample(now, 400.0, "sensor-a")
            monitor.tick()
            assertEquals(21_900.0, monitor.snapshots.value.totals!!.balance, 0.0)
            for (auto in listOf(true, false)) {
                monitor.onState(RideState.Paused(auto))
                now += 60_000
                monitor.sample(now, 0.0, "sensor-a")
                monitor.tick()
                assertEquals(21_900.0, monitor.snapshots.value.totals!!.balance, 0.0)
                assertNull(monitor.snapshots.value.available(60))
                monitor.onState(RideState.Recording)
                monitor.sample(now, 400.0, "sensor-a")
            }
            now += 1000
            monitor.sample(now, 400.0, "sensor-b") // source change must not bridge the interval
            monitor.tick()
            assertEquals(1.0, monitor.snapshots.value.totals!!.elapsed, 0.0)
            now += 3000
            monitor.tick()
            assertEquals(ReserveStatus.MISSING, monitor.snapshots.value.status)
            assertNull(monitor.snapshots.value.available(60))
            monitor.onState(RideState.Idle)
            assertNull(settings.restore())
            monitor.onState(RideState.Recording)
            assertEquals(22_000.0, monitor.snapshots.value.totals!!.balance, 0.0)
            assertFalse(monitor.snapshots.value.restored)
        } finally { monitor.onState(RideState.Idle); monitor.stop() }
    }

    @Test fun restartRestoresCheckpointButWarnsAndExplicitResetKeepsLockedParameters() {
        val first = ReserveMonitor(context) { now }
        first.onState(RideState.Idle)
        first.onState(RideState.Recording)
        first.sample(now, 400.0, "a")
        now += 1000
        first.sample(now, 400.0, "a")
        first.stop()
        val restored = ReserveMonitor(context) { now }
        try {
            restored.onState(RideState.Recording)
            assertTrue(restored.snapshots.value.restored)
            assertEquals(21_900.0, restored.snapshots.value.totals!!.balance, 0.0)
            settings.prefs.edit().putFloat("reserve_cp", 350f).commit()
            settings.requestReset()
            restored.tick()
            assertEquals(300.0, restored.snapshots.value.parameters!!.watts, 0.0)
            assertEquals(22_000.0, restored.snapshots.value.totals!!.balance, 0.0)
            assertTrue(restored.snapshots.value.restored)
            assertEquals(0.0, restored.snapshots.value.totals!!.elapsed, 0.0)
        } finally { restored.onState(RideState.Idle); restored.stop() }
    }

    @Test fun staleAndCorruptCheckpointsAreRejected() {
        val engine = ReserveEngine(ReserveParameters(300.0, 22_000.0, "Manual CP"))
        settings.save(engine, ReserveParameters(300.0, 22_000.0, "Manual CP"))
        val original = settings.prefs.getString("reserve_checkpoint", null)!!
        for (change in listOf<(JSONObject) -> Unit>(
            { it.put("at", System.currentTimeMillis() - 601_000) },
            { it.put("balance", 30_000) },
            { it.put("elapsed", -1) },
            { it.put("levels", org.json.JSONArray(listOf(1, 0, 0, 0, 0))) }
        )) {
            val data = JSONObject(original).also(change)
            settings.prefs.edit().putString("reserve_checkpoint", data.toString()).commit()
            assertNull(settings.restore())
        }
        settings.prefs.edit().putString("reserve_checkpoint", "broken").commit()
        assertNull(settings.restore())
    }
}
