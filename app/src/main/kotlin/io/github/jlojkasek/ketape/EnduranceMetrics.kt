package io.github.jlojkasek.ketape

internal data class EnduranceSnapshot(
    val drift: Double? = null,
    val zoneTwoPower: Double? = null
)

internal fun powerPerHeartRate(power: Double?, heartRate: Double?): Double? =
    if (power != null && power.isFinite() && power >= 0 &&
        heartRate != null && heartRate.isFinite() && heartRate > 0
    ) (power / heartRate).takeIf { it.isFinite() } else null

internal class EnduranceMetrics {
    private data class Sample(val watts: Double, val bpm: Double)

    private class Totals {
        var power = 0.0
        var heart = 0.0

        fun add(sample: Sample) {
            power += sample.watts
            heart += sample.bpm
        }

        fun remove(sample: Sample) {
            power -= sample.watts
            heart -= sample.bpm
        }
    }

    // As the ride grows, transfer the oldest sample in the second half to the first.
    private val secondHalf = ArrayDeque<Sample>()
    private val first = Totals()
    private val second = Totals()
    private var pairedSeconds = 0
    private var zone: IntRange? = null
    private var zonePower = 0.0
    private var zoneSeconds = 0

    fun sample(watts: Double?, bpm: Double?, heartRateZoneTwo: IntRange?): EnduranceSnapshot {
        if (zone != heartRateZoneTwo) {
            zone = heartRateZoneTwo
            zonePower = 0.0
            zoneSeconds = 0
        }
        if (watts != null && watts.isFinite() && watts >= 0) {
            if (bpm != null && bpm.isFinite() && bpm > 0) {
                if (heartRateZoneTwo != null && bpm >= heartRateZoneTwo.first && bpm <= heartRateZoneTwo.last) {
                    zonePower += watts
                    zoneSeconds++
                }
                val sample = Sample(watts, bpm)
                secondHalf.addLast(sample)
                second.add(sample)
                pairedSeconds++
                if (pairedSeconds % 2 == 0) {
                    val moved = secondHalf.removeFirst()
                    first.add(moved)
                    second.remove(moved)
                }
            }
        }
        val drift = if (pairedSeconds >= 1200 && first.power > 0 && second.power > 0) {
            val firstRatio = first.power / first.heart
            val secondRatio = second.power / second.heart
            (1 - secondRatio / firstRatio) * 100
        } else null
        return EnduranceSnapshot(drift, if (zoneSeconds > 0) zonePower / zoneSeconds else null)
    }
}
