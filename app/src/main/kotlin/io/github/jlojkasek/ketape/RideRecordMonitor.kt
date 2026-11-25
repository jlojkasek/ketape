package io.github.jlojkasek.ketape

import android.content.Context
import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.*

internal class RideRecordMonitor(context: Context, private val system: KarooSystemService) {
    val reserve = ReserveMonitor(context)
    private val records = IntervalsRecords(context)
    private val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rideValues = RideBestValues()
    private val continuity = RideContinuity()
    val incompleteRide: Boolean get() = continuity.incomplete
    @Volatile private var rideBests = emptyMap<Int, Int>()
    private val announced = mutableSetOf<Int>()
    private val pending = mutableListOf<RideBest>()
    private val metrics = mutableListOf<String>()
    private var profileConsumer: String? = null
    private var stateConsumer: String? = null
    private var idleConfirmed = false
    private var lastIdleSyncAttempt: Long? = null
    private var idleSync: Job? = null
    private var lastHeartReading: Long? = null
    @Volatile private var rideState: RideState = RideState.Idle
    @Volatile private var periods = records.periods
    private var power: Reading? = null
    private var heart: Reading? = null
    @Volatile private var notices = emptyMap<Boolean, Notice>()

    private data class Reading(val value: Double, val at: Long, val source: String?)
    private data class Notice(val best: RideBest, val expiresAt: Long)

    fun rideBestSnapshot(): Map<Int, Int> = rideBests
    fun comparisonPeriods(): List<String> = if (rideState == RideState.Idle) records.periods else periods

    fun current(power: Boolean): RideBest? =
        notices[power]?.takeIf { records.connected }?.takeIf { SystemClock.elapsedRealtime() < it.expiresAt }?.best

    fun start() {
        profileConsumer = system.addConsumer<ActiveRideProfile> { event ->
            scope.launch { reserve.indoor = event.profile.indoor }
        }
        stateConsumer = system.addConsumer<RideState> { state ->
            scope.launch { onRideState(state) }
        }
        scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (rideState == RideState.Recording) {
                    val watts = power?.takeIf { now - it.at <= 1500 }?.value
                    val bpm = heart?.takeIf { now - it.at <= 1500 && it.at != lastHeartReading }
                    lastHeartReading = heart?.at
                    checkRecords(rideValues.sample(now / 1000, watts, bpm?.value))
                }
                if (rideState != RideState.Idle && records.connected) showPending(now)
                syncWhileIdle(now)
                reserve.tick()
                delay(1000 - (SystemClock.elapsedRealtime() % 1000))
            }
        }
    }

    private fun syncWhileIdle(now: Long) {
        if (!idleConfirmed || !records.connected || idleSync?.isActive == true) return
        if (lastIdleSyncAttempt?.let { now - it < 15 * 60 * 1000L } == true) return
        lastIdleSyncAttempt = now
        idleSync = scope.launch { syncReferences() }
    }

    private suspend fun syncReferences(periods: List<String> = records.periods) {
        try {
            records.sync(periods = periods)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep cached references when Intervals.icu is unavailable.
        }
    }

    private fun onRideState(state: RideState) {
        idleConfirmed = state == RideState.Idle
        continuity.observe(state)
        reserve.onState(state)
        if (state == rideState) return
        if (rideState == RideState.Idle) {
            periods = records.periods
            val ridePeriods = periods
            if (records.connected) {
                scope.launch { syncReferences(ridePeriods) }
            }
        }
        rideState = state
        rideValues.reset()
        power = null
        heart = null
        lastHeartReading = null
        metrics.forEach(system::removeConsumer)
        metrics.clear()
        if (state == RideState.Recording) {
            metrics += subscribe(DataType.Type.POWER) {
                power = it
                reserve.sample(it?.at ?: SystemClock.elapsedRealtime(), it?.value, it?.source)
            }
            if (prefs.getBoolean(RECORD_HEART, false)) {
                metrics += subscribe(DataType.Type.HEART_RATE) { heart = it }
            }
        }
        if (state == RideState.Idle) {
            rideBests = emptyMap()
            announced.clear()
            pending.clear()
            notices = emptyMap()
        }
    }

    private fun subscribe(type: String, update: (Reading?) -> Unit): String =
        system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(type)) { event ->
            val point = (event.state as? StreamState.Streaming)?.dataPoint
            val reading = point?.singleValue?.let { Reading(it, SystemClock.elapsedRealtime(), point.sourceId) }
            scope.launch { if (rideState == RideState.Recording) update(reading) }
        }

    private fun checkRecords(values: List<RideBest>) {
        val selected = selectedPowerDurations(prefs.getStringSet(POWER_BEST_DURATIONS, null))
        for (best in values) {
            if (best.duration == HEART_RECORD && !prefs.getBoolean(RECORD_HEART, false)) continue
            if (best.value <= (rideBests[best.duration] ?: -1)) continue
            rideBests = rideBests + (best.duration to best.value)
            if (!records.connected || (best.duration != HEART_RECORD && best.duration !in selected)) continue
            val beatenPeriods = periods.filter { period ->
                val reference = records.reference(period, best.duration)
                reference != null && best.value > maxOf(reference, records.recentBest(period, best.duration))
            }
            if (beatenPeriods.isEmpty()) continue
            beatenPeriods.forEach { records.saveBest(it, best) }
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
        reserve.stop()
        scope.cancel()
        metrics.forEach(system::removeConsumer)
        profileConsumer?.let(system::removeConsumer)
        stateConsumer?.let(system::removeConsumer)
    }
}
