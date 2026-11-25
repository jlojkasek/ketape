package io.github.jlojkasek.ketape

import kotlin.math.*

internal data class ReserveParameters(val watts: Double, val capacity: Double, val source: String) {
    val valid get() = watts in 30.0..800.0 && capacity in 1_000.0..100_000.0
}

internal enum class ReserveStatus(val label: String, val symbol: String) {
    READY("Start a ride", "○"), CONFIGURE("Set CP and W′", "⚙"),
    PAUSED("Paused", "Ⅱ"), MISSING("No power data", "∅"),
    DEPLETING("Depleting", "↓"), RECOVERING("Recovering", "↑"),
    STABLE("Stable", "–"), FULL("Fully charged", "●")
}

internal data class ReservePoint(val at: Double, val percent: Double, val segment: Int)
internal data class ReserveTotals(
    val balance: Double, val minimum: Double = balance, val integral: Double = 0.0,
    val elapsed: Double = 0.0, val expended: Double = 0.0, val above: Double = 0.0,
    val levels: List<Double> = List(5) { 0.0 }
)

internal data class ReserveSnapshot(
    val parameters: ReserveParameters? = null, val totals: ReserveTotals? = null,
    val power: Double? = null, val status: ReserveStatus = ReserveStatus.READY,
    val history: List<ReservePoint> = emptyList(), val restored: Boolean = false
) {
    val percent get() = totals?.let { it.balance / requireNotNull(parameters).capacity * 100 }
    val delta get() = power?.let { it - requireNotNull(parameters).watts }
    val rate get() = when (status) {
        ReserveStatus.DEPLETING -> -requireNotNull(delta) * .06
        ReserveStatus.RECOVERING -> -requireNotNull(delta) *
            (1 - requireNotNull(totals).balance / requireNotNull(parameters).capacity) * .06
        else -> null
    }
    val forecast get(): Double? {
        val balance = totals?.balance ?: return null
        val capacity = requireNotNull(parameters).capacity
        val difference = delta ?: return null
        val seconds = when (status) {
            ReserveStatus.DEPLETING -> max(0.0, balance) / difference
            ReserveStatus.RECOVERING -> if (balance >= .9 * capacity) 0.0
                else capacity / -difference * ln((capacity - balance) / (.1 * capacity))
            else -> return null
        }
        return seconds.takeIf { it in 0.0..7200.0 }
    }

    fun available(seconds: Int): Double? =
        totals?.balance?.takeIf { it > 0 && seconds > 0 &&
            status !in listOf(ReserveStatus.PAUSED, ReserveStatus.MISSING, ReserveStatus.CONFIGURE, ReserveStatus.READY) }?.let {
            requireNotNull(parameters).watts + it / seconds
        }
}

internal class ReserveEngine(
    private val parameters: ReserveParameters,
    initial: ReserveTotals = ReserveTotals(parameters.capacity),
    private val restored: Boolean = false
) {
    private data class PowerInterval(val end: Double, val duration: Double, val watts: Double)
    private val power = ArrayDeque<PowerInterval>()
    private val history = ArrayDeque<ReservePoint>()
    private val thresholds = listOf(.8, .5, .25, .1)
    private var last: Double? = null
    private var latestPower: Double? = null
    private var segment = 0
    private var direction = ReserveStatus.STABLE
    var totals = initial
        private set

    fun gap() {
        if (last != null) segment++
        last = null
        latestPower = null
        power.clear()
        direction = ReserveStatus.STABLE
    }

    fun sample(at: Double, watts: Double?) {
        if (watts == null || !watts.isFinite() || watts < 0) {
            gap()
            return
        }
        val span = last?.let { at - it }
        if (span != null && span <= 0) return
        if (span != null && span > 2.5) gap()
        if (span != null && span <= 2.5) {
            integrate(watts, span)
            power.addLast(PowerInterval(at, span, watts))
        }
        last = at
        latestPower = watts
        while (power.firstOrNull()?.let { it.end <= at - 30 } == true) power.removeFirst()
        while (history.firstOrNull()?.let { it.at < at - 900 } == true || history.size >= 901) {
            history.removeFirst()
        }
        if (history.lastOrNull()?.let { at - it.at >= 1 || it.segment != segment } != false) {
            history.addLast(ReservePoint(at, totals.balance / parameters.capacity * 100, segment))
        }
    }

    private fun integrate(watts: Double, seconds: Double) {
        val start = totals.balance
        val capacity = parameters.capacity
        val difference = watts - parameters.watts
        val recovery = -difference / capacity
        val end = when {
            difference >= 0 -> start - difference * seconds
            else -> start + (capacity - start) * -expm1(-recovery * seconds)
        }
        val area = if (difference >= 0 || abs(recovery * seconds) < 1e-7) (start + end) * seconds / 2
            else capacity * seconds + (capacity - start) * expm1(-recovery * seconds) / recovery

        fun timeAbove(fraction: Double): Double {
            val boundary = fraction * capacity
            if (min(start, end) >= boundary) return seconds
            if (max(start, end) <= boundary) return 0.0
            return if (difference > 0) (start - boundary) / difference
                else seconds - ln((capacity - start) / (capacity - boundary)) / recovery
        }

        val cumulative = listOf(0.0) + thresholds.map(::timeAbove) + seconds
        totals = ReserveTotals(end, min(totals.minimum, end), totals.integral + area,
            totals.elapsed + seconds, totals.expended + max(0.0, difference) * seconds,
            totals.above + if (difference > 0) seconds else 0.0,
            totals.levels.zip(cumulative.zipWithNext { a, b -> max(0.0, b - a) }, Double::plus))
    }

    fun snapshot(now: Double, average: Int, paused: Boolean): ReserveSnapshot {
        if (last?.let { now - it > 2.5 } == true) gap()
        var duration = 0.0
        var work = 0.0
        for (reading in power) {
            val seconds = (reading.end - max(reading.end - reading.duration, now - average)).coerceAtLeast(0.0)
            duration += seconds
            work += seconds * reading.watts
        }
        val watts = if (duration > 0) work / duration else latestPower
        val delta = watts?.minus(parameters.watts)
        direction = when {
            paused -> ReserveStatus.PAUSED
            delta == null -> ReserveStatus.MISSING
            delta > (if (direction == ReserveStatus.DEPLETING) 4 else 8) -> ReserveStatus.DEPLETING
            totals.balance >= parameters.capacity * .995 -> ReserveStatus.FULL
            delta < (if (direction == ReserveStatus.RECOVERING) -4 else -8) -> ReserveStatus.RECOVERING
            else -> ReserveStatus.STABLE
        }
        return ReserveSnapshot(parameters, totals, watts, direction, history.toList(), restored)
    }
}
