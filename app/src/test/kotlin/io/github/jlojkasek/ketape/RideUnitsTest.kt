package io.github.jlojkasek.ketape

import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType.IMPERIAL
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType.METRIC
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RideUnitsTest {
    @Test fun distanceAndElevationPreferencesAreIndependent() {
        withLocale(Locale.US) {
            val metric = PreferredUnit(METRIC, METRIC, METRIC, METRIC)
            val imperial = PreferredUnit(IMPERIAL, IMPERIAL, IMPERIAL, IMPERIAL)
            assertReadings(null, "32.2 km/h", "16.1 km", "305 m")
            assertReadings(metric, "32.2 km/h", "16.1 km", "305 m")
            assertReadings(imperial, "20.0 mph", "10.0 mi", "1000 ft")
            assertReadings(imperial.copy(elevation = METRIC), "20.0 mph", "10.0 mi", "305 m")
            assertReadings(metric.copy(elevation = IMPERIAL), "32.2 km/h", "16.1 km", "1000 ft")
        }
    }

    @Test fun convertedReadingsUseTheSystemDecimalSeparator() {
        withLocale(Locale.GERMANY) {
            val mixed = PreferredUnit(IMPERIAL, METRIC, METRIC, METRIC)
            assertReadings(mixed, "20,0 mph", "10,0 mi", "305 m")
            assertEquals("–", format(null, Format.ELEVATION, mixed))
        }
    }

    private fun assertReadings(units: PreferredUnit?, speed: String, distance: String, elevation: String) {
        fun reading(value: Double, format: Format) = "${format(value, format, units)} ${format.unit(units)}"
        assertEquals(speed, reading(8.9408, Format.SPEED))
        assertEquals(distance, reading(16093.44, Format.DISTANCE))
        assertEquals(elevation, reading(304.8, Format.ELEVATION))
    }

    private fun withLocale(locale: Locale, body: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            body()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
