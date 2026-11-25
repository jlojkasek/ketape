package de.jannes.stacked

import android.content.Context
import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.*

internal class RideRecordMonitor(context: Context, private val system: KarooSystemService) {
    private val records = IntervalsRecords(context)
    private val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rideValues = RideBestValues()
    private val rideBests = mutableMapOf<Int, Int>()
    private val announced = mutableSetOf<Int>()
    private val pending = mutableListOf<RideBest>()
    private val metrics = mutableListOf<String>()
    private var stateConsumer: String? = null
    private var lastHeartReading: Long? = null
    private var rideState: RideState = RideState.Idle
    private var period = records.period
    private var power: Reading? = null
    private var heart: Reading? = null
    @Volatile private var notices = emptyMap<Boolean, Notice>()

    private data class Reading(val value: Double, val at: Long)
    private data class Notice(val best: RideBest, val expiresAt: Long)

    fun current(power: Boolean): RideBest? =
        notices[power]?.takeIf { records.connected }?.takeIf { SystemClock.elapsedRealtime() < it.expiresAt }?.best

    fun start() {
        stateConsumer = system.addConsumer<RideState> { state ->
            scope.launch { onRideState(state) }
        }
        scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (rideState == RideState.Recording && records.connected && records.syncedAt(period) > 0) {
                    val watts = power?.takeIf { now - it.at <= 1500 }?.value
                    val bpm = heart?.takeIf { now - it.at <= 1500 && it.at != lastHeartReading }
                    lastHeartReading = heart?.at
                    checkRecords(rideValues.sample(now / 1000, watts, bpm?.value))
                }
                if (rideState != RideState.Idle && records.connected) showPending(now)
                delay(1000 - (SystemClock.elapsedRealtime() % 1000))
            }
        }
    }

    private fun onRideState(state: RideState) {
        if (state == rideState) return
        if (rideState == RideState.Idle) {
            period = records.period
            val ridePeriod = period
            if (records.connected) {
                scope.launch {
                    try {
                        records.sync(period = ridePeriod)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the cached reference when the network is unavailable.
                    }
                }
            }
        }
        rideState = state
        rideValues.reset()
        power = null
        heart = null
        lastHeartReading = null
        metrics.forEach(system::removeConsumer)
        metrics.clear()
        if (state == RideState.Recording && records.connected) {
            metrics += subscribe(DataType.Type.POWER) { power = it }
            if (prefs.getBoolean(RECORD_HEART, false)) {
                metrics += subscribe(DataType.Type.HEART_RATE) { heart = it }
            }
        }
        if (state == RideState.Idle) {
            rideBests.clear()
            announced.clear()
            pending.clear()
            notices = emptyMap()
        }
    }

    private fun subscribe(type: String, update: (Reading?) -> Unit): String =
        system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(type)) { event ->
            val value = (event.state as? StreamState.Streaming)?.dataPoint?.singleValue
            val reading = value?.let { Reading(it, SystemClock.elapsedRealtime()) }
            scope.launch { if (rideState == RideState.Recording) update(reading) }
        }

    private fun checkRecords(values: List<RideBest>) {
        for (best in values) {
            if (best.duration == HEART_RECORD && !prefs.getBoolean(RECORD_HEART, false)) continue
            val reference = records.reference(period, best.duration) ?: continue
            val previous = maxOf(reference, records.recentBest(period, best.duration), rideBests[best.duration] ?: 0)
            if (best.value <= previous) continue
            rideBests[best.duration] = best.value
            records.saveBest(period, best)
            if (announced.add(best.duration)) {
                pending.add(best)
            } else {
                val queued = pending.indexOfFirst { it.duration == best.duration }
                if (queued >= 0) pending[queued] = best
            }
        }
    }

    private fun showPending(now: Long) {
        var beep = false
        for (isPower in listOf(true, false)) {
            if (notices[isPower]?.let { now < it.expiresAt } == true) continue
            val next = pending.indexOfFirst { (it.duration != HEART_RECORD) == isPower }
            if (next < 0) continue
            notices = notices + (isPower to Notice(pending.removeAt(next), now + 10_000))
            beep = true
        }
        if (beep && prefs.getBoolean(RECORD_SOUND, false)) {
            system.dispatch(PlayBeepPattern(listOf(PlayBeepPattern.Tone(1600, 140))))
        }
    }

    fun stop() {
        scope.cancel()
        metrics.forEach(system::removeConsumer)
        stateConsumer?.let(system::removeConsumer)
    }
}
