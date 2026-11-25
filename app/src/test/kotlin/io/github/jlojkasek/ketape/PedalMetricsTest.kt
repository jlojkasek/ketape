package io.github.jlojkasek.ketape

import io.hammerhead.karooext.models.DataType.Field
import io.hammerhead.karooext.models.DataType.Type
import org.junit.Assert.*
import org.junit.Test

class PedalMetricsTest {
    @Test fun nativeFieldsAndSmoothing() {
        assertEquals(setOf(Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE, Type.PEDAL_SMOOTHNESS),
            pedalSources(PedalLayout.PERFORMANCE, false))
        assertEquals(emptySet<String>(), pedalSources(PedalLayout.CLASSIC, false))
        assertEquals(3, pedalSources(PedalLayout.DYNAMICS, false).size)
        assertEquals(PedalLayout.CLASSIC, PedalLayout.from(null))
        assertEquals(PedalLayout.CLASSIC, PedalLayout.from("invalid"))
        val samples = PedalSamples()
        samples.update(Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE, mapOf(Field.PEDAL_POWER_BALANCE_LEFT to 47.5), 100)
        samples.update(Type.TORQUE_EFFECTIVENESS, mapOf(Field.TORQUE_EFFECTIVENESS_LEFT to 84.0,
            Field.TORQUE_EFFECTIVENESS_RIGHT to 87.0, Field.POWER to 300.0), 100)
        samples.update(Type.PEDAL_SMOOTHNESS, mapOf(Field.PEDAL_SMOOTHNESS_LEFT to 24.0,
            Field.PEDAL_SMOOTHNESS_RIGHT to 26.0), 100)
        val frame = samples.snapshot(200)
        assertEquals("48/52", frame.balanceText)
        assertEquals("PS 25 %", frame.combinedPsText)
        assertEquals("TE % 84/87", frame.teText)
        assertEquals("PS % 24/26", frame.psText)
        assertEquals(PedalMetrics(), samples.snapshot(5101))
    }
    @Test fun missingSidesNeverMirrorAndZeroIsValid() {
        assertEquals("84/–", pedalPair(84.0, null))
        assertEquals("–/87", pedalPair(null, 87.0))
        assertEquals("0/0", pedalPair(0.0, 0.0))
        assertEquals("–/–", PedalMetrics().balanceText)
        assertEquals("0/100", PedalMetrics(balanceLeft = 0.0).balanceText)
        assertEquals("100/0", PedalMetrics(balanceLeft = 100.0).balanceText)
        assertEquals("PS 0 %", PedalMetrics(psLeft = 0.0, psRight = 0.0).combinedPsText)
        assertEquals("PS – %", PedalMetrics(psLeft = 24.0).combinedPsText)
        assertEquals("PS 24 %", PedalMetrics(psLeft = 23.5, psRight = 24.5).combinedPsText)
        listOf(-1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(pedalPercent(it))
            assertEquals("–/–", PedalMetrics(balanceLeft = it).balanceText)
        }
        listOf(-1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertEquals("PS – %", PedalMetrics(psLeft = 24.0, psRight = it).combinedPsText)
        }
        assertEquals("85/87", pedalPair(84.5, 86.5))
        assertEquals("49/51", PedalMetrics(balanceLeft = 48.5).balanceText)
    }
    @Test fun omittedFieldsAndUnavailableStreamsClearPreviousValues() {
        val samples = PedalSamples()
        samples.update(Type.TORQUE_EFFECTIVENESS, mapOf(Field.TORQUE_EFFECTIVENESS_LEFT to 84.0,
            Field.TORQUE_EFFECTIVENESS_RIGHT to 87.0), 0)
        samples.update(Type.TORQUE_EFFECTIVENESS, mapOf(Field.TORQUE_EFFECTIVENESS_LEFT to 82.0), 100)
        assertEquals("TE % 82/–", samples.snapshot(100).teText)
        samples.update(Type.TORQUE_EFFECTIVENESS, null, 200)
        assertEquals("TE % –/–", samples.snapshot(200).teText)
        samples.update(Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE, mapOf(Field.POWER to 220.0), 200)
        assertEquals("–/–", samples.snapshot(200).balanceText)
    }
    @Test fun previewIncludesVariationOneSideZeroAndNoSensor() {
        val frames = (0 until 12).map { PreviewValues.pedals(it) }
        assertTrue(frames.map { it.combinedPsText }.distinct().size > 3)
        assertTrue(frames.any { it.teLeft != null && it.teRight == null })
        assertTrue(frames.any { it.psLeft == 0.0 && it.psRight == 0.0 })
        assertTrue(frames.any { it == PedalMetrics() })
        assertEquals(0.0, PreviewValues.cadence(8)!!, 0.0)
        assertNull(PreviewValues.cadence(10))
        assertEquals(PreviewValues.pedals(0), PreviewValues.pedals(12))
    }
}
