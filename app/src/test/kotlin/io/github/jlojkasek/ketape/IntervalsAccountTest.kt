package io.github.jlojkasek.ketape

import android.content.Context
import android.content.Intent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import java.io.IOException
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntervalsAccountTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
    private val key = KeyGenerator.getInstance("AES").generateKey()
    private var rejectOriginal = false
    private var reserveOffline = false
    private val requests = mutableListOf<String>()
    private val records = IntervalsRecords(context, ::request) { key }

    @Test fun aRejectedReplacementLeavesTheExistingAccountAndCacheIntact() = runBlocking {
        records.connect("original")
        populateCache()

        assertTrue(runCatching { records.connect("invalid") }.exceptionOrNull() is IntervalsKeyRejected)

        assertFalse(records.keyRejected)
        assertCacheRetained()
    }

    @Test fun replacingARejectedKeyForTheSameAccountPreservesBothCaches() = runBlocking {
        records.connect("original")
        populateCache()
        rejectOriginal = true
        assertTrue(runCatching { records.sync(force = true) }.exceptionOrNull() is IntervalsKeyRejected)
        assertTrue(records.keyRejected)
        assertCacheRetained()

        records.connect("replacement")

        assertFalse(records.keyRejected)
        assertCacheRetained()
    }

    @Test fun aLegacyConnectionCanVerifyItsAccountBeforeReplacingTheKey() = runBlocking {
        records.connect("original")
        prefs.edit().remove("intervals_account_id").apply()
        populateCache()

        records.connect("replacement")

        assertCacheRetained()
    }

    @Test fun changingAccountsClearsImportedValuesButKeepsManualParameters() = runBlocking {
        records.connect("original")
        populateCache()

        records.connect("other")

        assertTrue(records.connected)
        assertNull(records.reference("s0", 5))
        assertNull(ReservePreferences(context).cached)
        assertEquals(260f, prefs.getFloat("reserve_cp", 0f), 0f)
    }

    @Test fun bootSyncRefreshesYesterdayOrTodaysCacheAndRetriesOnlyTheUnfinishedImport() = runBlocking {
        records.connect("original")
        populateCache()
        val bootTime = System.currentTimeMillis() - 1_000L
        prefs.edit().putLong("intervals_cache_s0_at", bootTime - 10_000L)
            .putInt("intervals_cache_s0_max_duration", powerDurations.last())
            .putLong("intervals_cache_reserve_at", bootTime - 10_000L)
            .apply()
        val reserve = ReservePreferences(context)
        records.sync()
        assertEquals(400, records.reference("s0", 5))

        reserveOffline = true
        assertTrue(refreshIntervalsSinceBoot(records, reserve, bootTime))
        assertEquals(450, records.reference("s0", 5))
        assertEquals(300.0, requireNotNull(reserve.cached).watts, 0.0)

        reserveOffline = false
        requests.clear()
        assertFalse(refreshIntervalsSinceBoot(records, reserve, bootTime))
        assertEquals(listOf("/api/v1/athlete/0/sport-settings/Ride"), requests)
        assertEquals(315.0, requireNotNull(reserve.cached).watts, 0.0)

        requests.clear()
        assertFalse(refreshIntervalsSinceBoot(records, reserve, bootTime))
        assertTrue(requests.isEmpty())
    }

    @Test fun bootSchedulesNetworkConstrainedSyncOnlyWithAConnectedAccount() = runBlocking {
        val receiver = IntervalsBootReceiver()
        val boot = Intent(Intent.ACTION_BOOT_COMPLETED)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        receiver.onReceive(context, boot)
        assertTrue(scheduler.allPendingJobs.isEmpty())

        records.connect("original")
        receiver.onReceive(context, boot)

        val pending = scheduler.allPendingJobs.single()
        assertEquals(IntervalsBootSync::class.java.name, pending.service.className)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, pending.networkType)
    }

    @Test fun bootSyncAllowsThreeStartsAcrossServiceInstancesAndResetsAtTheNextBoot() = runBlocking {
        val receiver = IntervalsBootReceiver()
        val boot = Intent(Intent.ACTION_BOOT_COMPLETED)
        records.connect("original")
        receiver.onReceive(context, boot)

        assertEquals(1, BootSyncAttempts(context).start())
        assertEquals(2, BootSyncAttempts(context).start())
        assertEquals(3, BootSyncAttempts(context).start())
        assertNull(BootSyncAttempts(context).start())

        receiver.onReceive(context, boot)
        assertEquals(1, BootSyncAttempts(context).start())
    }

    @Test fun powerSyncFetchesBothRecordWindowsInOneRequestEvenWhenOnlySeasonIsDisplayed() = runBlocking {
        records.connect("original")
        requests.clear()
        records.sync()
        assertEquals(listOf("s0"), records.periods)
        assertEquals(listOf("/api/v1/athlete/0/power-curves.json?type=Ride&curves=s0,42d,all"), requests)
        assertEquals(430, records.tableRecordReference(RECENT_POWER_PERIOD, 5))
        assertEquals(500, records.tableRecordReference(ALL_TIME_POWER_PERIOD, 5))
    }

    @Test fun switchingToAllTimeReusesCachedPowerAndLoadsTheMissingHeartReferenceWhenEnabled() = runBlocking {
        records.connect("original")
        prefs.edit().putBoolean(RECORD_HEART, true).apply()
        records.sync()
        assertEquals(500, records.reference(ALL_TIME_POWER_PERIOD, 5))
        assertNull(records.reference(ALL_TIME_POWER_PERIOD, HEART_RECORD))

        prefs.edit().putString(RECORD_PERIOD, "42d,all").apply()
        requests.clear()
        records.sync()
        assertEquals(listOf(
            "/api/v1/athlete/0/power-curves.json?type=Ride&curves=all",
            "/api/v1/athlete/0/hr-curves.json?type=Ride&curves=all"
        ), requests)
        assertEquals(200, records.reference(ALL_TIME_POWER_PERIOD, HEART_RECORD))
        requests.clear()
        records.sync()
        assertTrue(requests.isEmpty())
    }

    @Test fun aCompletedRideRaisesTheThresholdForTheNextRideBeforeAnUpload() = runBlocking {
        records.connect("original")
        records.sync()
        records.saveTableRecords("i1", mapOf(5 to PowerRecordMark.ALL_TIME), mapOf(5 to 525))
        assertEquals(525, records.tableRecordReference(RECENT_POWER_PERIOD, 5))
        assertEquals(525, records.tableRecordReference(ALL_TIME_POWER_PERIOD, 5))
        assertNull(powerRecordMark(525, records.tableRecordReference(RECENT_POWER_PERIOD, 5),
            records.tableRecordReference(ALL_TIME_POWER_PERIOD, 5), previous = null))

        prefs.edit().putLong("intervals_local_table_42d_5_at",
            System.currentTimeMillis() - 43 * INTERVALS_SYNC_INTERVAL_MS).apply()
        assertEquals(430, records.tableRecordReference(RECENT_POWER_PERIOD, 5))
        assertEquals(525, records.tableRecordReference(ALL_TIME_POWER_PERIOD, 5))
        prefs.edit().putLong("intervals_cache_all_at",
            System.currentTimeMillis() - 3 * INTERVALS_SYNC_INTERVAL_MS).apply()
        assertNull(records.tableRecordReference(ALL_TIME_POWER_PERIOD, 5))
    }

    private fun request(path: String, token: String): JSONObject {
        requests.add(path)
        if (token == "invalid" || (token == "original" && rejectOriginal)) throw IntervalsKeyRejected()
        if (path.contains("/power-curves.json")) {
            val values = mapOf("s0" to 450, "42d" to 430, "all" to 500)
            val curves = JSONArray()
            path.substringAfter("curves=").split(",").reversed().forEach { period ->
                curves.put(JSONObject().put("id", period).put("secs", JSONArray().put(5))
                    .put("values", JSONArray().put(values.getValue(period))))
            }
            return JSONObject().put("list", curves)
        }
        if (path.contains("/hr-curves.json")) {
            val curves = JSONArray()
            path.substringAfter("curves=").split(",").forEach { period ->
                curves.put(JSONObject().put("id", period).put("secs", JSONArray().put(1))
                    .put("values", JSONArray().put(200)))
            }
            return JSONObject().put("list", curves)
        }
        if (path.endsWith("/sport-settings/Ride")) {
            if (reserveOffline) throw IOException("Offline")
            return JSONObject("""{"ftp":315,"w_prime":22500}""")
        }
        assertEquals("/api/v1/athlete/0", path)
        return JSONObject().put("id", if (token == "other") "i2" else "i1")
            .put("name", "Same display name")
    }

    private fun populateCache() {
        prefs.edit().putInt("intervals_cache_s0_5", 400)
            .putString("intervals_cache_reserve_settings", """{"ftp":300,"w_prime":22000}""")
            .putFloat("reserve_cp", 260f)
            .apply()
    }

    private fun assertCacheRetained() {
        assertTrue(records.connected)
        assertEquals(400, records.reference("s0", 5))
        assertEquals(300.0, requireNotNull(ReservePreferences(context).cached).watts, 0.0)
    }
}
