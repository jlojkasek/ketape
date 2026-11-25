package de.jannes.stacked

import android.app.Activity
import android.os.Bundle
import android.widget.*

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("stacked", MODE_PRIVATE)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 24, 18, 18)
        }
        content.addView(TextView(this).apply { text = "Stacked Fields"; textSize = 24f })
        content.addView(TextView(this).apply {
            text = "Im Karoo-Profil eine Datenseite bearbeiten und unter Stacked Fields das gewünschte Feld wählen. Leistung gibt es als 1 s, 3 s und 5 s. Ø und NP kommen direkt aus der aktuellen Karoo-Fahrt. Strecke zeigt Kilometer sowie gesammelte Anstiegs- und Abfahrts-Höhenmeter.\n\nDarstellung"
            textSize = 15f
        })
        val modes = listOf("auto", "light", "dark")
        val group = RadioGroup(this)
        listOf("Automatisch (Android-System)", "Hell", "Dunkel").forEachIndexed { index, label ->
            group.addView(RadioButton(this).apply {
                id = index + 100
                text = label
                isChecked = prefs.getString("theme", "auto") == modes[index]
            })
        }
        group.setOnCheckedChangeListener { _, id -> prefs.edit().putString("theme", modes[id - 100]).apply() }
        content.addView(group)
        content.addView(TextView(this).apply { text = "Zonenfarben für Leistung und Herzfrequenz"; textSize = 16f })
        val colorModes = listOf("background", "badge", "off")
        val colors = RadioGroup(this)
        listOf("Farbiger Hintergrund", "Nur farbiges Zonenabzeichen", "Aus").forEachIndexed { index, label ->
            colors.addView(RadioButton(this).apply {
                id = index + 200
                text = label
                isChecked = prefs.getString("zone_colors", "background") == colorModes[index]
            })
        }
        colors.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("zone_colors", colorModes[id - 200]).apply()
        }
        content.addView(colors)
        content.addView(Switch(this).apply {
            text = "Trittfrequenz: Links-Rechts-Verteilung"
            isChecked = prefs.getBoolean("cadence_balance", false)
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean("cadence_balance", enabled).apply()
            }
        })
        content.addView(TextView(this).apply {
            text = "Zeigt L/R-Leistungsanteile statt Ø und maximaler Trittfrequenz. Benötigt einen Leistungsmesser, der Balance-Daten liefert. Ohne Daten erscheint --."
            textSize = 13f
        })
        content.addView(TextView(this).apply {
            text = "Farbquelle\n${KarooPalette.load(this@SettingsActivity).status}"
            textSize = 13f
        })
        content.addView(TextView(this).apply {
            text = "Version 0.1.0 · Vorschau\nEinheiten: km und km/h. Automatik folgt dem Android-Nachtmodus. Zonenlimits kommen aus dem Karoo-Profil. Verifizierte Karoo-Farbressourcen werden bevorzugt; fehlen sie, bleibt die Ersatzpalette aktiv. Die Farbzonen der Leistung beziehen sich auf den gewählten 1-, 3- oder 5-s-Wert. Für gute Lesbarkeit zunächst mindestens ein Viertel der Bildschirmhöhe verwenden."
            textSize = 13f
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
