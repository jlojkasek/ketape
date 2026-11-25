package io.github.jlojkasek.ketape

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
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
private const val API_KEY = "intervals_api_key"
private const val KEY_ALIAS = "stacked-intervals-token"
private const val DAY_MS = 86_400_000L
private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

internal class IntervalsRecords(context: Context) {
    private val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
    val connected: Boolean get() = prefs.contains(API_KEY)
    val accountName: String? get() = prefs.getString(ACCOUNT_NAME, null)
    val periods: List<String> get() = when (prefs.getString(RECORD_PERIOD, "s0")) {
        "both" -> listOf("s0", "42d")
        "42d" -> listOf("42d")
        else -> listOf("s0")
    }

    suspend fun connect(apiKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val profile = request("/api/v1/athlete/0", apiKey)
            val editor = clearAccount().putString(API_KEY, encrypt(apiKey))
            (profile.opt("name") as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString(ACCOUNT_NAME, it) }
            editor.apply()
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock { clearAccount().apply() }
    }

    private fun clearAccount(): SharedPreferences.Editor {
        val editor = prefs.edit().remove(API_KEY).remove(ACCOUNT_NAME)
        prefs.all.keys.filter { it.startsWith("intervals_cache_") || it.startsWith("intervals_local_") }
            .forEach { editor.remove(it) }
        return editor
    }

    fun syncedAt(period: String): Long = prefs.getLong("intervals_cache_${period}_at", 0)
    fun reference(period: String, duration: Int): Int? =
        prefs.getInt("intervals_cache_${period}_$duration", 0).takeIf { it > 0 }

    fun recentBest(period: String, duration: Int): Int {
        val key = "intervals_local_${period}_$duration"
        val age = System.currentTimeMillis() - prefs.getLong("${key}_at", 0)
        return if (age in 0 until 2 * DAY_MS) prefs.getInt(key, 0) else 0
    }

    fun saveBest(period: String, best: RideBest) {
        val key = "intervals_local_${period}_${best.duration}"
        prefs.edit().putInt(key, best.value).putLong("${key}_at", System.currentTimeMillis()).apply()
    }

    suspend fun sync(force: Boolean = false, periods: List<String> = this.periods) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val due = periods.filter {
                force || prefs.getInt("intervals_cache_${it}_max_duration", 0) != powerDurations.last() ||
                    now - syncedAt(it) !in 0 until DAY_MS
            }
            if (due.isEmpty()) return@withLock
            val apiKey = readApiKey() ?: return@withLock
            for (period in due) {
                storeBestValues(prefs.edit(), period, now, fetchBestValues(apiKey, period))
            }
        }
    }

    private fun fetchBestValues(apiKey: String, period: String): Map<Int, Int> {
        val power = request("/api/v1/athlete/0/power-curves.json?type=Ride&curves=$period", apiKey)
        val bestPower = curveValues(power, powerDurations, "watts")
        if (!prefs.getBoolean(RECORD_HEART, false)) return bestPower
        val heart = request("/api/v1/athlete/0/hr-curves.json?type=Ride&curves=$period", apiKey)
        val bestHeart = curveValues(heart, listOf(1), "bpm")[1] ?: return bestPower
        return bestPower + (HEART_RECORD to bestHeart)
    }

    private fun storeBestValues(editor: SharedPreferences.Editor, period: String, now: Long,
                                values: Map<Int, Int>) {
        (powerDurations + HEART_RECORD).forEach { duration ->
            editor.putInt("intervals_cache_${period}_$duration", values[duration] ?: 0)
        }
        editor.putInt("intervals_cache_${period}_max_duration", powerDurations.last())
            .putLong("intervals_cache_${period}_at", now).apply()
    }

    private fun request(path: String, apiKey: String): JSONObject {
        val connection = URL("https://intervals.icu$path").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val credentials = Base64.encodeToString("API_KEY:$apiKey".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            connection.setRequestProperty("Authorization", "Basic $credentials")
            val status = connection.responseCode
            if (status == 401 || (status == 403 && path == "/api/v1/athlete/0")) {
                throw IntervalsKeyRejected()
            }
            if (status !in 200..299) throw IntervalsHttpError(status)
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readApiKey(): String? {
        val stored = prefs.getString(API_KEY, null) ?: return null
        val bytes = Base64.decode(stored, B64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }

    private fun encrypt(apiKey: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8)), B64)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object { val mutex = Mutex() }
}

internal class IntervalsKeyRejected : IOException()
internal class IntervalsHttpError(val status: Int) : IOException()

internal fun curveValues(response: JSONObject, durations: List<Int>, metric: String): Map<Int, Int> {
    val curves = response.optJSONArray("list") ?: response.getJSONArray("curves")
    val result = mutableMapOf<Int, Int>()
    for (index in 0 until curves.length()) {
        val curve = curves.getJSONObject(index)
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
