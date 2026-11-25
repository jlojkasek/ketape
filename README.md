# kEtape

**Bessere Fahrdaten für deinen Karoo.**

kEtape bringt Stacked Fields und Palmarès auf den Hammerhead Karoo. Kombiniere Live-Werte und Fahrtstatistiken in kompakten Feldern oder nutze eine ganze Seite für deine Leistungsbestwerte. Mit Intervals.icu siehst du während der Fahrt, wie nah du an deinen Bestwerten aus der Saison oder den letzten 42 Tagen bist.

- **Fahrdaten kompakt sehen.** Leistung mit Durchschnitt und NP, Puls und Tempo mit Durchschnitt und Maximum, Strecke mit Höhenmetern und Trittfrequenz auf einen Blick.
- **Die Anzeige an dein Training anpassen.** Wähle die Glättung des Hauptwerts und lass dir Leistungs- und Pulszonen farbig anzeigen. Mit einem kompatiblen Leistungsmesser kannst du die Links-rechts-Balance samt Warnschwelle einblenden.
- **Leistungsbestwerte während der Fahrt vergleichen.** Palmarès zeigt deinen besten Wert der aktuellen Fahrt neben deinen Intervals.icu-Referenzen. Du wählst die Dauern von 1 Sekunde bis 60 Minuten und entscheidest zwischen einem kompakten, automatisch wechselnden Feld und der Tabelle.
- **Neue Bestwerte erkennen.** Leistungs- und Herzfrequenzfelder können einen übertroffenen Referenzwert kurz einblenden. Den Signalton kannst du separat aktivieren.

## Einrichtung

1. Die APK aus den [Veröffentlichungen](https://github.com/jlojkasek/ketape-releases/releases) auf dem Karoo installieren.
2. Im Fahrprofil eine Datenseite bearbeiten und unter **kEtape** die gewünschten Felder hinzufügen.
3. Unter **Anzeige** Glättung, Zonenfarben und Balance einstellen. Für historische Referenzwerte im Tab **Palmarès** mit Intervals.icu verbinden.

## Vorschau

`preview.html` im Browser öffnen. Alle Werte darin sind Beispieldaten. Diese Vorschau dient der Layoutentscheidung und testet weder Android RemoteViews noch die Karoo-Verbindung.

## Build

Benötigt werden JDK 17 oder 21, Android SDK 35 und der enthaltene Gradle-Wrapper 8.9. Das Projekt ist in Android Studio eingerichtet; `local.properties` und `.gradle/config.properties` enthalten die lokalen SDK-/JDK-Pfade und werden nicht verteilt.

Die SDK-Quellen 1.1.9 liegen unter `vendor/karoo-ext` (Commit und Apache-Lizenz siehe `UPSTREAM.md` bzw. `LICENSE`). Dadurch sind keine GitHub-Packages-Zugangsdaten erforderlich. Zum Bauen `scripts/build.sh` ausführen. Das Skript übernimmt bei Bedarf das von Android Studio eingerichtete Java und führt Unit-Tests, APK-Build und Lint aus.

Quellen: https://github.com/hammerheadnav/karoo-ext und https://hammerheadnav.github.io/karoo-ext/

## Auf dem Karoo testen

1. Entwickleroptionen und USB-Debugging am Karoo einschalten.
2. Karoo per USB-Datenkabel anschließen und die Debugging-Freigabe am Gerät bestätigen.
3. `scripts/build.sh`, dann `scripts/install.sh` ausführen. Die Installation aktualisiert ausschließlich `io.github.jlojkasek.ketape`.
4. In der App Hell oder Dunkel wählen. Automatik folgt zunächst nur dem Android-Nachtmodus.
5. Im Fahrprofil eine Datenseite bearbeiten und unter kEtape ein Feld wählen. Zunächst ein großes Feld oder eine Viertelseite verwenden.
6. Kurze Testaufzeichnung mit Sensoren starten. Watt, Ø und NP mit eingebauten Karoo-Feldern vergleichen. Danach Pause, Fortsetzen, Seitenwechsel, Sensorverlust, Fahrtende und neue Fahrt prüfen.
7. Die Glättung des Leistungsfelds in den App-Einstellungen umstellen und die Datenseite erneut öffnen. Fahrt-Ø und NP mit den nativen Feldern vergleichen.

## Offene Prüfungen und Grenzen

- SDK-Signaturen, Abhängigkeiten und Manifest wurden im erfolgreichen Build geprüft. Die Datenabfragen nutzen `OnStreamState.StartStreaming`.
- Die Zeiteinheit von ELAPSED_TIME muss am Gerät bestätigt werden; der aktuelle Formatter erwartet Sekunden.
- Metrische Einheiten sind fest eingestellt, die Übernahme der Benutzereinheiten folgt später.
- Hell und Dunkel sind manuell wählbar. Android-Automatik ist keine bestätigte Übernahme des Karoo-Fahrdesigns. Karoo-Pills fehlen noch. Zonenfarben sind unten beschrieben.
- Enge halbe Felder und alle Rastergrößen müssen am Gerät auf Abschneiden geprüft werden.
- Der Hauptwert wird bei mehr als fünf Sekunden ohne Stream-Update ausgeblendet. Das Verhalten auch bei Pause und stationärer Fahrt testen.
- Der numerische Stream des Stacked Fields übernimmt den Hauptwert, erzeugt keine zusätzlichen FIT-Felder.

Als nächste Felder bieten sich Höhenmeter (Anstieg, Abstieg, Höhe), Runde (Rundenleistung, Runden-Ø, Runden-NP) und Ultra-Übersicht (Strecke, Gesamtzeit inklusive Pausen, Reststrecke) an.

## Zonenfarben

Leistung und Herzfrequenz beobachten `UserProfile`. Die Zone wird anhand der dort hinterlegten absoluten Min-/Max-Grenzen aus dem angezeigten Hauptwert ermittelt. Es werden keine FTP- oder Max-HF-Standardzonen geraten. Profiländerungen greifen bei der nächsten Darstellung.

In der App: farbiger Hintergrund (Standard), farbiges Zonenabzeichen oder Aus. Im Abzeichenmodus erscheint die Zone als Z1, Z2 usw. Die Textfarbe wird mit dem höheren Schwarz-/Weiß-Kontrast gewählt. Fahrt-Ø und NP bleiben die originalen Karoo-Werte. Ohne gültige Zonen oder aktuellen Sensorwert wird neutral dargestellt. An gemeinsam genutzten Grenzen gewinnt die höhere Zone; Lücken und Werte außerhalb aller Grenzen bleiben neutral.

Die Palette ist an Karoo angelehnt und fest hinterlegt: Leistung grau, blau, grün, gelb, orange, rot, violett; HF grau, blau, grün, gelb, rot. Das SDK liefert mit `UserProfile.Zone(min, max)` keine nativen Farbwerte. Exakte Karoo-Farbtöne und abweichende Zonenanzahlen sind am Gerät abzugleichen. Über sieben Leistungs- bzw. fünf HF-Zonen wird neutral dargestellt, sobald kein Paletteneintrag vorhanden ist.

Prüfen: `gradle :app:testDebugUnitTest :app:assembleDebug`. Tests decken Grenzen, fehlende/ungültige Werte, Profilwechsel, Null-Watt und Textkontrast ab. Diese Unit-Tests sind bestanden. Am Gerät zusätzlich Sensorverlust, alle drei Farbmodi, Hell/Dunkel und Profiländerungen prüfen.

## Tatsächliche Karoo-Systemfarben auslesen

Android bietet `PackageManager.getResourcesForApplication()` und `Resources.getIdentifier()` an. Damit kann die Erweiterung verifizierte Farbressourcen aus installierten Karoo-Apps direkt laden. Das ist ein vorbereiteter Integrationsweg, noch kein auf Karoo bestätigter Zugriff: Paketnamen, Ressourcennamen und ihre Zuordnung zu Zonen sind noch nicht bekannt. Farben könnten auch im Code oder in Grafiken stecken; dann funktioniert dieser Weg für diese Firmware nicht.

Vorbereitet sind:

- `scripts/karoo_colors.py`: liest per ADB die Hammerhead-Paketliste, kopiert deren APKs und legt mit `aapt2 dump resources` ein Ressourceninventar an. Kandidaten werden anhand von Namen markiert, nicht automatisch als Zonenfarben übernommen.
- `KarooPalette.kt`: lädt nach Hinterlegung einer bestätigten Zuordnung die echten Farben über Android. Eine Palette wird nur vollständig übernommen. Fehlende Ressourcen oder nicht lesbare Pakete führen zur bisherigen Ersatzpalette. Die aktive Farbquelle steht in den App-Einstellungen.
- `--apply`: übernimmt eine verifizierte Zuordnung und ergänzt die nötige Android-Paketsichtbarkeit im Manifest. Es werden Ressourcen gelesen, kein Code einer anderen App geladen.

Mit angeschlossenem und freigegebenem Karoo, ADB und Android Build Tools:

```sh
python3 scripts/karoo_colors.py
```

Unter `diagnostics/karoo-colors/` entstehen APK-Kopien, vollständige Ressourcen-Dumps und `inventory.json`. Es werden keine Fahrten oder Profildaten abgerufen. `--package` begrenzt die Abfrage auf ein bestimmtes Paket; `--serial` wählt das Gerät.

Anschließend müssen die sieben Leistungsfarben und fünf HF-Farben anhand der Ressourcen und ihrer Verwendung in der Karoo-App identifiziert werden. Nur Namen wie `zone_1` reichen nicht als Bestätigung: auch Tag/Nacht, kräftige/helle Varianten und die Verwendung für Hintergründe müssen geprüft werden. Eine Mapping-Datei enthält die Listen `power` und `heart`, jeweils in Zonenreihenfolge. Jeder Eintrag hat `package` (installiertes Paket) und `resource` (bestätigter Farbname ohne `color/`). Es gibt bewusst noch keine Zuordnung mit geratenen Namen.

```sh
python3 scripts/karoo_colors.py --apply /pfad/zur/bestaetigten-zuordnung.json
```

Danach APK neu bauen und installieren. Nach Firmware-Änderungen werden die Farben beim erneuten Öffnen des Feldes aus den Systemressourcen gelesen. Werden Namen geändert oder entfernt, greift die Ersatzpalette. Themespezifische Attribute oder dynamisch berechnete Farbverläufe werden damit noch nicht automatisch übernommen. Der Vergleich mit nativen Karoo-Feldern bleibt erforderlich.

Referenzen:
- https://developer.android.com/reference/android/content/pm/PackageManager#getResourcesForApplication(java.lang.String)
- https://developer.android.com/reference/android/content/res/Resources#getIdentifier(java.lang.String,java.lang.String,java.lang.String)
- https://developer.android.com/tools/aapt2
- https://developer.android.com/training/package-visibility/declaring

Die Python-Tests laufen mit `python3 -m unittest discover -s scripts/tests -v`. Android-Build ist erfolgreich; Gerätetest steht noch aus.

## Minimales Layout

Die obere Beschriftungszeile entfällt. Links neben dem Hauptwert steht ein monochromes Symbol (Blitz, Herz, Tacho, Strecke, Kurbel). Die kleine Einheit bleibt rechts am Hauptwert. Im Abzeichenmodus sitzt die Zonennummer in der unteren Wertezeile. Symbole folgen dem Kontrast von Hell/Dunkel bzw. Zonenhintergrund. Die Glättung des Hauptwerts lässt sich in den Einstellungen unter **Anzeige** wählen.

Das Streckenfeld zeigt gefahrene Kilometer groß und darunter links die während der aktuellen Fahrt gesammelten Anstiegshöhenmeter (`ELEVATION_GAIN`, ↗ m) und rechts die Abfahrts-Höhenmeter (`ELEVATION_LOSS`, ↘ m). Diese Auslegung bezieht sich auf bereits gefahrene Höhenmeter, nicht auf geplante Routenhöhenmeter. Fahrtzeit und Ø-Geschwindigkeit entfallen dort.

## Zahlen ohne Zeilenumbruch

Hauptwert, Zusatzwerte, Einheit und Zonenabzeichen sind auf eine Zeile begrenzt. Die Zahlen verwenden tabellarische Ziffern, soweit die Systemschrift das unterstützt. Horizontales Scrollen ist deaktiviert, sodass Android beim Autosizing die tatsächliche Feldbreite berücksichtigt. Haupt- und Zusatzwerte werden bis auf 8 sp verkleinert. Die Zusatzzeile reserviert unabhängig von der Ziffernzahl 20 dp; sie kann keine zusätzliche Zeile aufspannen. Die Hauptschrift wird passend zum verfügbaren Platz berechnet; Android passt die Zusatzwerte automatisch an. Bei extremem Platzmangel bleibt als letzte Grenze ein sichtbares Auslassungszeichen statt eines Zeilenumbruchs. Beliebig kleine Felder können nicht sämtliche Werte lesbar anzeigen.

`NumberLayoutTest` prüft echte Android-Textlayouts nach wiederholtem `RemoteViews.reapply`: 9 → 10, 99 → 100, 999 → 1000, 9999 → 10000, fehlender Wert und Rückkehr zu zwei Ziffern. Enthalten sind halbe/volle Breiten (120, 160, 280 dp), mehrere Höhen, Strecke mit Nachkommastelle und Höhenmetern sowie eingeblendete Zonenabzeichen. Geprüft werden eine Zeile, vollständiger Text, keine Ellipse, kein horizontales/vertikales Abschneiden und konstante Hauptzeilenhöhe. Ausführen mit `gradle :app:connectedDebugAndroidTest` auf angeschlossenem Karoo oder Android-Emulator. Dieser Gerätetest ist vorbereitet, aber noch nicht auf einem Gerät ausgeführt.

Die HTML-Vorschau hat einen Schalter „Ziffernwechsel testen“. Sie verkleinert Zahlen nach der tatsächlich gemessenen Browserbreite und verhindert Textumbruch. Sie ersetzt den Android-Test nicht.

## Trittfrequenz und Links-Rechts-Verteilung

Das Icon zeigt eine Kurbel mit zwei Pedalen und einer Drehbewegung. In den Einstellungen kann „Trittfrequenz: Links-Rechts-Verteilung“ aktiviert werden. Der Hauptwert bleibt die Trittfrequenz; unten erscheinen `L 48%` und `R 52%` statt Ø und Maximum. Die Einstellung greift beim nächsten Rendern ohne Neustart.

Die Verteilung bezeichnet die Leistung der beiden Beine, nicht die Zahl der Pedalumdrehungen. Der separate Karoo-Stream `PEDAL_POWER_BALANCE` enthält mehrere Werte; deshalb wird explizit `PEDAL_POWER_BALANCE_LEFT` gelesen. Rechts wird als Ergänzung zu 100 % berechnet. Es werden keine Balance-Werte aus der Trittfrequenz geschätzt. Der aktuelle Parser erwartet Prozentwerte (0 bis 100); Einheit und Verhalten des jeweiligen Leistungsmessers müssen beim Gerätetest mit dem nativen Karoo-Balancefeld abgeglichen werden.

Ohne gültige Balance, bei gemeldeten Null-Watt oder nach fünf Sekunden ohne Balance-Update erscheinen `L --` und `R --`. Ein einseitiger Leistungsmesser liefert möglicherweise keine solche Messung. In der Vorschau ist L/R mit Beispieldaten umschaltbar. `PedalBalanceTest` prüft die Summen, Grenzwerte und ungültige Daten; die Kotlin-Unit-Tests sind bestanden.

## Verifizierter Testbuild vom 25. November 2026

`assembleDebug`, `testDebugUnitTest` und `lintDebug` erfolgreich. Acht Unit-Tests, null Testfehler; Lint ohne Fehler (38 Hinweise, unter anderem kleine Schriften, API-26-Autosizing bei minSdk 23 und deutsche UI-Texte). APK-Signaturen v1/v2 mit `apksigner verify` geprüft. Paket `de.jannes.stacked`, Version 0.1.0 (1). Der Build verwendet Java 21, Gradle 8.9 und AGP 8.7.3. Ein Karoo-Test und die instrumentierten Android-Layouttests sind noch offen.

## GitHub

Das Repository enthält Quellcode, vendortes Karoo-SDK samt Lizenz, Gradle-Wrapper, Tests, Diagnose-/Installationsskripte und die HTML-Vorschau. Lokale SDK-/JDK-Pfade, Build-Caches, Schlüssel und erzeugte APKs sind ausgeschlossen.

Unter GitHub Actions läuft bei Push und Pull Request sowie manuell der Workflow „Build and checks“: Java 21, Unit-Tests, Android Lint und Debug-APK. Nach erfolgreichem Lauf steht die APK als Workflow-Artefakt `ketape-debug` bereit. Für manuell installierte APKs und CI-Builds können unterschiedliche Debug-Signaturschlüssel verwendet werden; ein Update über eine anders signierte Installation erfordert deren vorherige Deinstallation. Veröffentlichte APKs werden lokal mit dem bestehenden App-Zertifikat signiert.

## Version 0.1.1

Icon, Hauptwert und Einheit bilden jetzt eine kompakte Gruppe. Deren Ausrichtung folgt der Karoo-Feldeinstellung; die Einheit bleibt mit 4 dp Abstand direkt an der Zahl. Die Hauptschrift wird anhand der tatsächlichen Feldabmessungen, Textbreite sowie des Platzes für Icon und Einheit berechnet. Die kleinen Zusatzwerte bleiben in einer festen, einzeiligen Zeile mit eigener Größenanpassung. Das Layout gilt für Leistung, Herzfrequenz, Geschwindigkeit, Strecke und Trittfrequenz.

Im Datenseiten-Editor wechseln die Beispiele etwa alle 2,2 Sekunden. Die Werte decken Ziffernwechsel, Dezimalzahlen, größere Strecken, Höhenmeter, Leistungs-/HF-Zonen und optional L/R-Verteilung ab. Die Beispieldaten gelten ausschließlich für `ViewConfig.preview`; während einer Fahrt kommen weiterhin echte Karoo-Werte zum Einsatz. Der Layouttest prüft zusätzlich den Abstand zwischen Icon, Zahl und Einheit für W, bpm, km/h, km und rpm. Er läuft sowohl unter Robolectric mit nativer Android-Schriftmessung (API 34) als auch als vorbereiteter Gerätetest.

Version 0.1.1: Build, zwölf Unit-Tests einschließlich 900 Layout-/Wertkombinationen unter Robolectric sowie Lint erfolgreich (null Fehler). APK lokal unter `dist/karoo-stacked-fields-0.1.1-debug.apk`. Die Darstellung auf dem Karoo ist nach Installation des Updates erneut zu prüfen.

## Karoo-Updates ab Version 0.1.2

Die App registriert eine feste Manifest-Adresse beim Karoo. Der Terminalbefehl `python3 scripts/release.py` baut, prüft und veröffentlicht einen getaggten Stand. APK und Manifest erscheinen im separaten öffentlichen Download-Repository. Die Schritte für den nächsten Release stehen in [releases/README.md](releases/README.md).

## Intervals.icu-Bestwerte

Wähle in den Einstellungen der Erweiterung **Intervals.icu verbinden** und
gib deinen persönlichen API-Schlüssel aus den Intervals.icu-Entwicklereinstellungen
ein. Die App prüft den Schlüssel über das Athletenprofil und speichert ihn
verschlüsselt auf dem Karoo. Unter **Synchronisierte Bestwerte** zeigt sie
die geladenen Werte und das Datum des letzten Abgleichs. **Intervals.icu trennen** entfernt den Schlüssel
und die zwischengespeicherten Bestwerte vom Gerät. Den Schlüssel selbst kannst
du in Intervals.icu widerrufen oder neu erzeugen.

Die Erweiterung liest Rad-Bestwerte über die Intervals.icu-API und speichert die
Vergleichswerte auf dem Karoo. Für Palmarès stehen die aktuelle Saison, die letzten 42 Tage oder beide Zeiträume zur Wahl. Die Leistungsdauern sind von 1 Sekunde bis 60 Minuten auswählbar.
Optionale Pulsbestwert-Meldungen erscheinen im Herzfrequenz-Feld.
