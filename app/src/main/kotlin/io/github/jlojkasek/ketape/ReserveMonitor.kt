package io.github.jlojkasek.ketape

import android.content.Context
import android.os.SystemClock
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ReserveMonitor(context: Context, private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val settings = ReservePreferences(context)
    private val records = IntervalsRecords(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var indoor = false
    private var sourceId: String? = null
    private var state: RideState? = null
    private var parameters: ReserveParameters? = null
    private var engine: ReserveEngine? = null
    private var lastSave = 0.0
    private var sync: Job? = null
    private val current = MutableStateFlow(ReserveSnapshot())
    val snapshots = current.asStateFlow()
    private var lastSyncAttempt: Long? = null

    fun onState(next: RideState) {
        if (next == state) return
        val previous = state
        state = next
        rideInProgress = next != RideState.Idle
        sourceId = null
        if (next == RideState.Idle) {
            engine = null
            parameters = null
            settings.clear()
            current.value = ReserveSnapshot()
        } else if (previous == null || previous == RideState.Idle) {
            sync?.cancel()
            val reset = settings.consumeReset()
            val restored = if (previous == null && !reset) settings.restore() else null
            parameters = restored?.first ?: settings.parametersFor(indoor)
            engine = restored?.second ?: parameters?.let {
                ReserveEngine(it, restored = previous == null)
            }
            lastSave = 0.0
        }
        engine?.gap()
        tick()
    }

    fun sample(at: Long, watts: Double?, source: String?) {
        if (state != RideState.Recording) return
        if (sourceId != source) engine?.gap()
        sourceId = source
        engine?.sample(at / 1000.0, watts)
    }

    fun tick() {
        if (state == RideState.Idle) {
            val age = System.currentTimeMillis() - settings.syncedAt
            val now = clock()
            if (!settings.manual && records.connected && age !in 0 until 86_400_000L &&
                sync?.isActive != true && (lastSyncAttempt?.let { now - it >= 900_000 } != false)) {
                lastSyncAttempt = now
                sync = scope.launch {
                    try {
                        records.syncReserve()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Retain the previous valid parameters.
                    }
                }
            }
            return
        }
        if (state == null) return
        if (settings.consumeReset()) {
            // Use the ride-locked parameters. A deliberate reset still does not reconstruct the ride.
            engine = parameters?.let { ReserveEngine(it, restored = true) }
            lastSave = 0.0
            sourceId = null
        }
        val now = clock() / 1000.0
        val active = engine
        current.value = active?.snapshot(now, settings.average, state is RideState.Paused)
            ?: ReserveSnapshot(status = ReserveStatus.CONFIGURE)
        if (active != null && (lastSave == 0.0 || now - lastSave >= 15)) save(now)
    }

    private fun save(now: Double) {
        val active = engine ?: return
        settings.save(active, requireNotNull(parameters))
        lastSave = now
    }

    fun stop() {
        save(clock() / 1000.0)
        scope.cancel()
    }
    companion object {
        @Volatile var rideInProgress = false
            private set
    }
}
