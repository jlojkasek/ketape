package io.github.jlojkasek.ketape

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

private const val ACCOUNT_NAME = "intervals_account_name"
private const val ACCOUNT_ID = "intervals_account_id"
internal const val INTERVALS_API_KEY = "intervals_api_key"
internal const val INTERVALS_KEY_REJECTED = "intervals_key_rejected"
private const val ATHLETE_PROFILE = "/api/v1/athlete/0"
private const val KEY_ALIAS = "stacked-intervals-token"
internal const val INTERVALS_SYNC_INTERVAL_MS = 86_400_000L
internal const val INTERVALS_IDLE_CHECK_MS = 15 * 60 * 1000L
internal const val RECENT_POWER_PERIOD = "42d"
internal const val ALL_TIME_POWER_PERIOD = "all"
private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

internal class IntervalsRecords(
    context: Context,
    private val request: (String, String) -> JSONObject = ::intervalsRequest,
    private val encryptionKey: () -> SecretKey = ::intervalsEncryptionKey
) {
    private val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
    val connected: Boolean get() = prefs.contains(INTERVALS_API_KEY)
    val keyRejected: Boolean get() = connected && prefs.getBoolean(INTERVALS_KEY_REJECTED, false)
    val accountName: String? get() = prefs.getString(ACCOUNT_NAME, null)
    val accountId: String? get() = prefs.getString(ACCOUNT_ID, null)
    val comparisonPeriods: List<ComparisonPeriod> get() = ComparisonPeriod.selected(prefs.getString(RECORD_PERIOD, null))
    val periods: List<String> get() = comparisonPeriods.map { it.id }

    suspend fun connect(apiKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val profile = request(ATHLETE_PROFILE, apiKey)
            val athleteId = profile.athleteId()
            // Older installs stored only a display name, which cannot identify an account.
            val previousId = prefs.getString(ACCOUNT_ID, null) ?: legacyAccountId(apiKey, athleteId)
            currentCoroutineContext().ensureActive()
            val encrypted = encrypt(apiKey)
            synchronized(prefs) {
                val editor = if (athleteId == previousId) prefs.edit() else clearAccount()
                editor.putString(INTERVALS_API_KEY, encrypted).putString(ACCOUNT_ID, athleteId)
                    .remove(INTERVALS_KEY_REJECTED).remove(ACCOUNT_NAME)
                (profile.opt("name") as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString(ACCOUNT_NAME, it) }
                editor.apply()
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock { synchronized(prefs) { clearAccount().apply() } }
    }

    private fun clearAccount(): SharedPreferences.Editor {
        val editor = prefs.edit().remove(INTERVALS_API_KEY).remove(ACCOUNT_ID).remove(ACCOUNT_NAME)
            .remove(INTERVALS_KEY_REJECTED)
        prefs.all.keys.filter { it.startsWith("intervals_cache_") || it.startsWith("intervals_local_") }
            .forEach { editor.remove(it) }
        return editor
    }

    fun syncedAt(period: String): Long = prefs.getLong("intervals_cache_${period}_at", 0)
    fun stale(period: String): Boolean {
        val at = syncedAt(period)
        return at > 0 && System.currentTimeMillis() - at !in 0 until 2 * INTERVALS_SYNC_INTERVAL_MS
    }
    fun reference(period: String, duration: Int): Int? =
        prefs.getInt("intervals_cache_${period}_$duration", 0).takeIf { it > 0 }

    fun recentBest(period: String, duration: Int): Int {
        val key = "intervals_local_${period}_$duration"
        val age = System.currentTimeMillis() - prefs.getLong("${key}_at", 0)
        return if (age in 0 until 2 * INTERVALS_SYNC_INTERVAL_MS) prefs.getInt(key, 0) else 0
    }

    fun saveBest(period: String, best: RideBest) {
        val key = "intervals_local_${period}_${best.duration}"
        prefs.edit().putInt(key, best.value).putLong("${key}_at", System.currentTimeMillis()).apply()
    }

    fun tableRecordReference(period: String, duration: Int): Int? {
        if (syncedAt(period) == 0L || stale(period)) return null
        val reference = reference(period, duration) ?: return null
        val key = "intervals_local_table_${period}_$duration"
        val localBest = prefs.getInt(key, 0)
        val age = System.currentTimeMillis() - prefs.getLong("${key}_at", 0)
        return if (period == ALL_TIME_POWER_PERIOD || age in 0 until 42 * INTERVALS_SYNC_INTERVAL_MS)
            maxOf(reference, localBest) else reference
    }

    fun saveTableRecords(account: String, records: Map<Int, PowerRecordMark>, bests: Map<Int, Int>) {
        if (records.isEmpty()) return
        synchronized(prefs) {
            if (account != accountId) return
            val editor = prefs.edit()
            val now = System.currentTimeMillis()
            for ((duration, mark) in records) {
                val value = bests[duration] ?: continue
                val recentKey = "intervals_local_table_${RECENT_POWER_PERIOD}_$duration"
                val recentAge = now - prefs.getLong("${recentKey}_at", 0)
                if (recentAge !in 0 until 42 * INTERVALS_SYNC_INTERVAL_MS || value > prefs.getInt(recentKey, 0)) {
                    editor.putInt(recentKey, value).putLong("${recentKey}_at", now)
                }
                if (mark == PowerRecordMark.ALL_TIME) {
                    val allTimeKey = "intervals_local_table_${ALL_TIME_POWER_PERIOD}_$duration"
                    editor.putInt(allTimeKey, maxOf(value, prefs.getInt(allTimeKey, 0)))
                }
            }
            editor.apply()
        }
    }

    suspend fun sync(
        force: Boolean = false,
        periods: List<String> = this.periods,
        refreshSince: Long = 0
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val heartEnabled = prefs.getBoolean(RECORD_HEART, false)
            val needsAllTimeHeart = heartEnabled && ALL_TIME_POWER_PERIOD in periods &&
                !prefs.getBoolean("intervals_cache_all_hr_synced", false)
            val due = (periods + RECENT_POWER_PERIOD + ALL_TIME_POWER_PERIOD).distinct().filter {
                force || prefs.getInt("intervals_cache_${it}_max_duration", 0) != powerDurations.last() ||
                    syncedAt(it) !in maxOf(now - INTERVALS_SYNC_INTERVAL_MS + 1, refreshSince)..now ||
                    (it == ALL_TIME_POWER_PERIOD && needsAllTimeHeart)
            }
            val needsAccountId = !prefs.contains(ACCOUNT_ID)
            if ((keyRejected && !force) || (due.isEmpty() && !needsAccountId)) return@withLock
            val apiKey = readApiKey() ?: return@withLock
            if (needsAccountId) {
                val id = requestForAccount(ATHLETE_PROFILE, apiKey).athleteId()
                currentCoroutineContext().ensureActive()
                prefs.edit().putString(ACCOUNT_ID, id).apply()
            }
            if (due.isNotEmpty()) {
                val power = fetchCurves(apiKey, "power", due, powerDurations, "watts")
                val heart = if (heartEnabled) {
                    val heartPeriods = if (ALL_TIME_POWER_PERIOD in periods) due else due - ALL_TIME_POWER_PERIOD
                    fetchCurves(apiKey, "hr", heartPeriods, listOf(1), "bpm")
                } else emptyMap()
                currentCoroutineContext().ensureActive()
                val editor = prefs.edit()
                for (period in due) {
                    val values = power.getValue(period)
                    val bestHeart = heart[period]?.get(1)
                    if (period == ALL_TIME_POWER_PERIOD) {
                        editor.putBoolean("intervals_cache_all_hr_synced", period in heart)
                    }
                    storeBestValues(editor, period, now,
                        if (bestHeart == null) values else values + (HEART_RECORD to bestHeart))
                }
                editor.apply()
            }
        }
    }

    suspend fun syncReserve(refreshSince: Long? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (ReserveMonitor.rideInProgress) throw ReserveSyncError(R.string.reserve_sync_after_ride)
            if (refreshSince != null &&
                prefs.getLong("intervals_cache_reserve_at", 0) in refreshSince..System.currentTimeMillis()) {
                return@withLock
            }
            val apiKey = readApiKey() ?: throw ReserveSyncError(R.string.reserve_connect_first)
            val settings = requestForAccount("/api/v1/athlete/0/sport-settings/Ride", apiKey)
            if (reserveSportParameters(settings) == null) {
                throw ReserveSyncError(R.string.reserve_invalid_sport)
            }
            currentCoroutineContext().ensureActive()
            if (ReserveMonitor.rideInProgress) throw ReserveSyncError(R.string.reserve_sync_after_ride)
            prefs.edit().putString("intervals_cache_reserve_settings", settings.toString())
                .putLong("intervals_cache_reserve_at", System.currentTimeMillis()).apply()
        }
    }

    private suspend fun fetchCurves(apiKey: String, type: String, periods: List<String>,
                                    durations: List<Int>, metric: String): Map<String, Map<Int, Int>> {
        if (periods.isEmpty()) return emptyMap()
        val ids = periods.joinToString(",")
        val response = requestForAccount("/api/v1/athlete/0/$type-curves.json?type=Ride&curves=$ids", apiKey)
        return curveValuesByPeriod(response, periods, durations, metric)
    }

    private fun legacyAccountId(newKey: String, athleteId: String): String? {
        val previousKey = try {
            readApiKey()
        } catch (_: java.security.GeneralSecurityException) {
            null
        } ?: return null
        if (previousKey == newKey) return athleteId
        return try {
            request(ATHLETE_PROFILE, previousKey).athleteId()
        } catch (_: IntervalsKeyRejected) {
            null
        }
    }

    private fun JSONObject.athleteId(): String = optString("id", "").takeIf { it.isNotBlank() && it != "null" }
        ?: throw org.json.JSONException("Missing athlete ID")

    private suspend fun requestForAccount(path: String, apiKey: String): JSONObject = try {
        request(path, apiKey).also {
            currentCoroutineContext().ensureActive()
            if (keyRejected) prefs.edit().remove(INTERVALS_KEY_REJECTED).apply()
        }
    } catch (error: IntervalsKeyRejected) {
        currentCoroutineContext().ensureActive()
        prefs.edit().putBoolean(INTERVALS_KEY_REJECTED, true).apply()
        throw error
    }

    private fun storeBestValues(editor: SharedPreferences.Editor, period: String, now: Long,
                                values: Map<Int, Int>) {
        (powerDurations + HEART_RECORD).forEach { duration ->
            editor.putInt("intervals_cache_${period}_$duration", values[duration] ?: 0)
        }
        editor.putInt("intervals_cache_${period}_max_duration", powerDurations.last())
            .putLong("intervals_cache_${period}_at", now)
    }

    private fun readApiKey(): String? {
        val stored = prefs.getString(INTERVALS_API_KEY, null) ?: return null
        val bytes = Base64.decode(stored, B64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, bytes, 0, 12))
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }

    private fun encrypt(apiKey: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8)), B64)
    }

    private companion object { val mutex = Mutex() }
}

private fun intervalsRequest(path: String, apiKey: String): JSONObject {
    val connection = URL("https://intervals.icu$path").openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        val credentials = Base64.encodeToString("API_KEY:$apiKey".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        connection.setRequestProperty("Authorization", "Basic $credentials")
        val status = connection.responseCode
        if (status == 401 || (status == 403 && path == ATHLETE_PROFILE)) {
            throw IntervalsKeyRejected()
        }
        if (status !in 200..299) throw IntervalsHttpError(status)
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } finally {
        connection.disconnect()
    }
}

private fun intervalsEncryptionKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
    }.generateKey()
}

internal class IntervalsKeyRejected : IOException()
internal class IntervalsHttpError(val status: Int) : IOException()

internal fun curveValues(response: JSONObject, durations: List<Int>, metric: String): Map<Int, Int> {
    return curveValues(curveObjects(response), durations, metric)
}

internal fun curveValuesByPeriod(response: JSONObject, periods: List<String>, durations: List<Int>,
                                 metric: String): Map<String, Map<Int, Int>> {
    val curves = curveObjects(response)
    val byId = curves.groupBy { it.optString("id") }
    if (periods.size == 1 && byId.keys == setOf("")) {
        return mapOf(periods.single() to curveValues(curves, durations, metric))
    }
    if (curves.isNotEmpty() && byId.keys.none { it in periods }) {
        throw org.json.JSONException("Response contains no requested curve identifiers")
    }
    return periods.associateWith { period -> curveValues(byId[period].orEmpty(), durations, metric) }
}

private fun curveObjects(response: JSONObject): List<JSONObject> {
    val curves = response.optJSONArray("list") ?: response.getJSONArray("curves")
    return List(curves.length(), curves::getJSONObject)
}

private fun curveValues(curves: List<JSONObject>, durations: List<Int>, metric: String): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    for (curve in curves) {
        val data = curve.optJSONObject("curve") ?: curve
        val seconds = data.getJSONArray("secs")
        val values = data.optJSONArray("values") ?: data.getJSONArray(metric)
        for (point in 0 until seconds.length()) {
            val duration = seconds.optInt(point)
            val value = values.optInt(point)
            if (duration in durations && value > (result[duration] ?: 0)) result[duration] = value
        }
    }
    return result
}

internal class ReserveSyncError(val messageRes: Int) : IOException()
