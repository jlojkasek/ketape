package io.github.jlojkasek.ketape

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.*

internal class RideRecordMonitor(context: Context, private val system: KarooSystemService) {
    val reserve = ReserveMonitor(context)
    @Volatile var enduranceSnapshot = EnduranceSnapshot()
        private set
    @Volatile var enduranceRatio: Double? = null
        private set
    @Volatile var preferredUnits: UserProfile.PreferredUnit? = null
        private set
    private var normalizedPower: Double? = null
    private var averageHeartRate: Double? = null
    private var endurance = EnduranceMetrics()
    private var heartRateZoneTwo: IntRange? = null
    private var userProfileConsumer: String? = null
    private val records = IntervalsRecords(context)
    private val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rideValues = RideBestValues()
    private val continuity = RideContinuity()
    val incompleteRide: Boolean get() = continuity.incomplete
    @Volatile private var rideBests = emptyMap<Int, Int>()
    @Volatile private var tableRecords = TableRecords()
    private var lastTableBests = rideBests
    private var lastTableReferences: Triple<String?, Long, Long>? = null
    private val announced = mutableSetOf<Int>()
    private val pending = mutableListOf<RideBest>()
    private val metrics = mutableListOf<String>()
    private var profileConsumer: String? = null
    private var stateConsumer: String? = null
    private var idleConfirmed = false
    private var nextAutomaticSyncAt = 0L
    private var automaticSync: Job? = null
    private var lastHeartReading: Long? = null
    private var pauseStartedSecond: Long? = null
    @Volatile private var rideState: RideState = RideState.Idle
    @Volatile private var periods = records.periods
    private var power: Reading? = null
    private var heart: Reading? = null
    @Volatile private var notices = emptyMap<Boolean, Notice>()

    private data class Reading(val value: Double, val at: Long, val source: String?)
    private data class Notice(val best: RideBest, val expiresAt: Long)
    private data class TableRecords(val account: String? = null, val marks: Map<Int, PowerRecordMark> = emptyMap())

    fun rideBestSnapshot(): Map<Int, Int> = rideBests
    fun tableRecordSnapshot(): Map<Int, PowerRecordMark> =
        tableRecords.let { if (it.account == records.accountId) it.marks else emptyMap() }

    fun current(power: Boolean): RideBest? =
        notices[power]?.takeIf { records.connected }?.takeIf { SystemClock.elapsedRealtime() < it.expiresAt }?.best

    fun start() {
        profileConsumer = system.addConsumer<ActiveRideProfile> { event ->
            scope.launch { reserve.indoor = event.profile.indoor }
        }
        userProfileConsumer = system.addConsumer<UserProfile> { profile ->
            preferredUnits = profile.preferredUnit
            scope.launch {
                heartRateZoneTwo = profile.heartRateZones.getOrNull(1)?.let { it.min..it.max }
                    ?.takeUnless { it.isEmpty() }
            }
        }
        stateConsumer = system.addConsumer<RideState> { state ->
            scope.launch { onRideState(state) }
        }
        scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (rideState == RideState.Recording) {
                    val watts = power?.takeIf { now - it.at <= 1500 }?.value
                    val freshHeart = heart?.takeIf { now - it.at <= 1500 }
                    if (!continuity.incomplete) {
                        enduranceSnapshot = endurance.sample(watts, freshHeart?.value, heartRateZoneTwo)
                    }
                    enduranceRatio = powerPerHeartRate(normalizedPower, averageHeartRate)
                    val bpm = freshHeart?.takeIf { it.at != lastHeartReading }
                    lastHeartReading = heart?.at
                    checkRecords(rideValues.sample(now / 1000, watts, bpm?.value?.takeIf { prefs.getBoolean(RECORD_HEART, false) }))
                } else {
                    pauseStartedSecond?.let { checkRecords(rideValues.samplePause(it, now / 1000)) }
                }
                if (rideState != RideState.Idle) {
                    updateTableRecords()
                    if (records.connected) showPending(now)
                }
                if (idleConfirmed) syncAutomatically(now)
                reserve.tick()
                delay(1000 - (SystemClock.elapsedRealtime() % 1000))
            }
        }
    }

    private fun syncAutomatically(now: Long) {
        if (now < nextAutomaticSyncAt || !records.connected || records.keyRejected ||
            automaticSync?.isActive == true) return
        nextAutomaticSyncAt = now + INTERVALS_IDLE_CHECK_MS
        automaticSync = scope.launch {
            val periods = records.periods
            val before = periods.map(records::syncedAt)
            try {
                records.sync(periods = periods)
                if (periods.map(records::syncedAt) != before) {
                    Log.i("KetapePalmares", "Automatic sync refreshed references")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                nextAutomaticSyncAt = SystemClock.elapsedRealtime() + INTERVALS_SYNC_INTERVAL_MS
                Log.i("KetapePalmares", "Automatic sync failed; cached values retained")
            }
        }
    }

    private fun onRideState(state: RideState) {
        idleConfirmed = state == RideState.Idle
        continuity.observe(state)
        reserve.onState(state)
        if (state == rideState) return
        val now = SystemClock.elapsedRealtime()
        val nextSecond = (now + 999) / 1000
        if (rideState == RideState.Idle) {
            periods = records.periods
            syncAutomatically(now)
        }
        if (state is RideState.Paused) {
            if (pauseStartedSecond == null) pauseStartedSecond = nextSecond
        } else {
            pauseStartedSecond?.let {
                checkRecords(rideValues.samplePause(it, nextSecond - 1))
                updateTableRecords()
            }
            pauseStartedSecond = null
        }
        rideState = state
        power = null
        heart = null
        lastHeartReading = null
        normalizedPower = null
        averageHeartRate = null
        metrics.forEach(system::removeConsumer)
        metrics.clear()
        if (state == RideState.Recording) {
            metrics += subscribe(DataType.Type.POWER) {
                power = it
                reserve.sample(it?.at ?: SystemClock.elapsedRealtime(), it?.value, it?.source)
            }
            metrics += subscribe(DataType.Type.HEART_RATE) { heart = it }
            metrics += subscribe(DataType.Type.NORMALIZED_POWER) { normalizedPower = it?.value }
            metrics += subscribe(DataType.Type.AVERAGE_HR) { averageHeartRate = it?.value }
        }
        if (state == RideState.Idle) {
            rideValues.reset()
            tableRecords.account?.let { records.saveTableRecords(it, tableRecords.marks, rideBests) }
            tableRecords = TableRecords()
            lastTableReferences = null
            enduranceRatio = null
            endurance = EnduranceMetrics()
            enduranceSnapshot = EnduranceSnapshot()
            rideBests = emptyMap()
            announced.clear()
            pending.clear()
            notices = emptyMap()
        }
    }

    private fun updateTableRecords() {
        val account = records.accountId ?: return
        val references = Triple(account, records.syncedAt(RECENT_POWER_PERIOD), records.syncedAt(ALL_TIME_POWER_PERIOD))
        val bests = rideBests
        if (lastTableBests === bests && lastTableReferences == references) return
        val existing = tableRecords.takeIf { it.account == account }?.marks.orEmpty()
        val marks = bests.mapNotNull { (duration, value) ->
            if (duration == HEART_RECORD) null else powerRecordMark(
                value, records.tableRecordReference(RECENT_POWER_PERIOD, duration),
                records.tableRecordReference(ALL_TIME_POWER_PERIOD, duration), existing[duration]
            )?.let { duration to it }
        }.toMap()
        tableRecords = TableRecords(account, marks)
        lastTableBests = bests
        lastTableReferences = references
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
        userProfileConsumer?.let(system::removeConsumer)
        stateConsumer?.let(system::removeConsumer)
    }
}
