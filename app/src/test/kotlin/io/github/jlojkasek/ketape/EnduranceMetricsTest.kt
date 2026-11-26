package io.github.jlojkasek.ketape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnduranceMetricsTest {
    @Test
    fun driftComparesPairedRideHalvesAfterTwentyMinutes() {
        val metrics = EnduranceMetrics()
        repeat(600) { assertNull(metrics.sample(180.0, 120.0, 160..200).drift) }
        repeat(599) { assertNull(metrics.sample(180.0, 135.0, 160..200).drift) }
        assertNull(metrics.sample(180.0, null, 160..200).drift)
        assertEquals(100.0 / 9, metrics.sample(180.0, 135.0, 160..200).drift!!, 0.001)
    }

    @Test
    fun zoneAverageFiltersByPairedHeartRateAndIncludesCoasting() {
        val metrics = EnduranceMetrics()
        val zone = 120..140
        assertNull(metrics.sample(180.0, 130.0, null).zoneTwoPower)
        assertNull(metrics.sample(130.0, null, zone).zoneTwoPower)
        assertNull(metrics.sample(130.0, 141.0, zone).zoneTwoPower)
        assertNull(metrics.sample(null, 130.0, zone).zoneTwoPower)
        metrics.sample(180.0, 120.0, zone)
        metrics.sample(220.0, 140.0, zone)
        assertEquals(200.0, metrics.sample(140.0, 119.0, zone).zoneTwoPower!!, 0.001)
        assertEquals(400.0 / 3, metrics.sample(0.0, 130.0, zone).zoneTwoPower!!, 0.001)
        assertNull(metrics.sample(210.0, 130.0, 145..160).zoneTwoPower)
    }

    @Test
    fun ratioAcceptsZeroPowerAndRequiresValidPulse() {
        assertEquals(1.5, powerPerHeartRate(210.0, 140.0)!!, 0.001)
        assertEquals(0.0, powerPerHeartRate(0.0, 140.0)!!, 0.001)
        assertNull(powerPerHeartRate(210.0, 0.0))
        assertNull(powerPerHeartRate(Double.NaN, 140.0))
    }
}
