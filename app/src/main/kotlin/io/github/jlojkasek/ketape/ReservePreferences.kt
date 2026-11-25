package io.github.jlojkasek.ketape

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class ReservePreferences(context: Context) {
    val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
    val manual get() = prefs.getBoolean("reserve_manual", false)
    val average get() = prefs.getInt("reserve_average", 10).takeIf { it in listOf(3, 10, 30) } ?: 10
    val graph get() = prefs.getInt("reserve_graph", 5).takeIf { it in listOf(3, 5, 10, 15) } ?: 5
    val colors get() = prefs.getBoolean("reserve_colors", true)
    val threshold get() = prefs.getInt("reserve_threshold", 20).coerceIn(5, 50)
    fun displayKey(): List<Any?> = listOf(average, graph, colors, threshold, prefs.getString("theme", "auto"))
    val syncedAt get() = prefs.getLong("intervals_cache_reserve_at", 0)
    private val sportSettings get() = prefs.getString("intervals_cache_reserve_settings", null)
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
    val cached get() = reserveSportParameters(sportSettings)
    val indoorCached get() = reserveSportParameters(sportSettings, indoor = true)
    val parameters get() = parametersFor(false)
    fun parametersFor(indoor: Boolean) = if (manual) ReserveParameters(
        prefs.getFloat("reserve_cp", 0f).toDouble(), prefs.getFloat("reserve_kj", 0f) * 1000.0, "Manual CP"
    ).takeIf { it.valid } else if (indoor) indoorCached else cached

    fun save(engine: ReserveEngine, parameters: ReserveParameters) {
        val t = engine.totals
        val data = JSONObject().put("at", System.currentTimeMillis()).put("watts", parameters.watts)
            .put("capacity", parameters.capacity).put("source", parameters.source)
            .put("balance", t.balance).put("min", t.minimum).put("integral", t.integral)
            .put("elapsed", t.elapsed).put("expended", t.expended).put("above", t.above)
            .put("levels", JSONArray(t.levels))
        prefs.edit().putString("reserve_checkpoint", data.toString()).apply()
    }

    fun restore(): Pair<ReserveParameters, ReserveEngine>? = runCatching {
        val data = JSONObject(prefs.getString("reserve_checkpoint", null) ?: return null)
        if (System.currentTimeMillis() - data.getLong("at") !in 0..600_000) return null
        val parameters = ReserveParameters(data.getDouble("watts"), data.getDouble("capacity"),
            data.getString("source")).takeIf { it.valid } ?: return null
        val levels = data.getJSONArray("levels")
        val totals = ReserveTotals(data.getDouble("balance"), data.getDouble("min"), data.getDouble("integral"),
            data.getDouble("elapsed"), data.getDouble("expended"), data.getDouble("above"),
            List(5) { levels.getDouble(it) })
        require(listOf(totals.balance, totals.minimum, totals.integral, totals.elapsed,
            totals.expended, totals.above).all { it.isFinite() })
        require(totals.balance <= parameters.capacity && totals.minimum <= totals.balance)
        require(totals.elapsed >= 0 && totals.expended >= 0 && totals.above in 0.0..totals.elapsed)
        require(totals.levels.all { it.isFinite() && it >= 0 })
        require(kotlin.math.abs(totals.levels.sum() - totals.elapsed) < 0.01)
        parameters to ReserveEngine(parameters, totals, restored = true)
    }.getOrNull()

    fun requestReset() {
        prefs.edit().remove("reserve_checkpoint").putBoolean("reserve_reset_requested", true).apply()
    }

    fun consumeReset(): Boolean {
        if (!prefs.getBoolean("reserve_reset_requested", false)) return false
        prefs.edit().remove("reserve_reset_requested").apply()
        return true
    }

    fun clear() = prefs.edit().remove("reserve_checkpoint").apply()
}

internal fun reserveSportParameters(settings: JSONObject?, indoor: Boolean = false): ReserveParameters? {
    settings ?: return null
    val indoorFtp = settings.optDouble("indoor_ftp").takeIf { indoor && it in 30.0..800.0 }
    return ReserveParameters(indoorFtp ?: settings.optDouble("ftp"), settings.optDouble("w_prime"),
        if (indoorFtp != null) "Intervals.icu indoor FTP" else "Intervals.icu FTP").takeIf { it.valid }
}
