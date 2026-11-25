package io.github.jlojkasek.ketape

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import org.json.JSONObject

/** Reads verified resource names from installed Karoo apps. No guessed IDs or foreign code loading. */
internal object KarooPalette {
    data class Result(val power: List<Int>?, val heart: List<Int>?, val status: String)

    fun load(context: Context): Result {
        val mapping = try {
            JSONObject(context.assets.open("karoo-zone-resources.json").bufferedReader().use { it.readText() })
        } catch (_: java.io.IOException) {
            return Result(null, null, "Ersatzpalette: Karoo-Ressourcenzuordnung fehlt")
        } catch (_: org.json.JSONException) {
            return Result(null, null, "Ersatzpalette: Ressourcenzuordnung ungültig")
        }
        fun read(key: String, count: Int): List<Int>? {
            val entries = mapping.optJSONArray(key) ?: return null
            if (entries.length() != count) return null
            return try {
                (0 until count).map { index ->
                    val entry = entries.getJSONObject(index)
                    val pkg = entry.getString("package")
                    val name = entry.getString("resource")
                    val resources = context.packageManager.getResourcesForApplication(pkg)
                    val id = resources.getIdentifier(name, "color", pkg)
                    if (id == 0) return null
                    // Resolve references and ColorStateLists using the current resource configuration.
                    val color = resources.getColorStateList(id, null).defaultColor
                    if ((color ushr 24) != 255) return null
                    color
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: Resources.NotFoundException) {
                null
            } catch (_: org.json.JSONException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
        val power = read("power", 7)
        val heart = read("heart", 5)
        return Result(power, heart, "Leistung: ${if (power != null) "Karoo-Ressourcen" else "Ersatzpalette"}\n" +
            "Herzfrequenz: ${if (heart != null) "Karoo-Ressourcen" else "Ersatzpalette"}")
    }
}
