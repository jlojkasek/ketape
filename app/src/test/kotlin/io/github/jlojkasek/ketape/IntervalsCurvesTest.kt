package io.github.jlojkasek.ketape

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntervalsCurvesTest {
    @Test fun parsesIntervalsPowerCurveList() {
        val response = JSONObject("""{"list":[{"secs":[1,5,60],"values":[950,740,420]}],"activities":{}}""")
        assertEquals(mapOf(1 to 950, 5 to 740, 60 to 420), curveValues(response, powerDurations, "watts"))
    }

    @Test fun parsesHeartRateAndHandlesEmptyHistory() {
        val response = JSONObject("""{"list":[{"secs":[1,5],"bpm":[191,188]}]}""")
        assertEquals(mapOf(1 to 191), curveValues(response, listOf(1), "bpm"))
        assertEquals(emptyMap<Int, Int>(), curveValues(JSONObject("""{"list":[]}"""), listOf(1), "bpm"))
    }
}
