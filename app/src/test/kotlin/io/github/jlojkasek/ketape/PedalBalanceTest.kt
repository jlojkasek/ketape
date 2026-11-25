package io.github.jlojkasek.ketape

import org.junit.Assert.*
import org.junit.Test

class PedalBalanceTest {
    @Test fun sharesAlwaysSumToOneHundred() {
        listOf(0.0, 1.0, 48.0, 49.5, 50.0, 50.5, 99.0, 100.0).forEach {
            val balance = requireNotNull(pedalBalance(it))
            assertEquals(100, balance.first + balance.second)
        }
        assertEquals(48 to 52, pedalBalance(48.0))
        assertEquals(0 to 100, pedalBalance(0.0))
        assertEquals(100 to 0, pedalBalance(100.0))
    }
    @Test fun missingOrInvalidDataDoesNotInventBalance() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 101.0).forEach {
            assertNull(pedalBalance(it))
        }
    }

    @Test fun warningRequiresAnEnabledThresholdAndExceedsItOnEitherSide() {
        assertFalse(exceedsBalanceThreshold(null, 5))
        assertFalse(exceedsBalanceThreshold(pedalBalance(44.0), 0))
        assertFalse(exceedsBalanceThreshold(pedalBalance(45.0), 5))
        assertFalse(exceedsBalanceThreshold(pedalBalance(55.0), 5))
        assertTrue(exceedsBalanceThreshold(pedalBalance(44.0), 5))
        assertTrue(exceedsBalanceThreshold(pedalBalance(56.0), 5))
    }
}
