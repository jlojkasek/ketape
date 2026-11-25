# kEtape

Stacked Fields, Palmarès und Réserve für Hammerhead Karoo. Entwicklung und Veröffentlichung erfolgen lokal im Terminal. GitHub dient als Quellsicherung, unabhängige Prüfung und APK-Download; ein weiterer Hostingdienst ist nicht nötig.

## Felder

- **Stacked Fields:** Leistung mit Fahrt-Ø und NP, Puls und Tempo mit Ø/Maximum, Strecke mit Anstiegs-/Abfahrtshöhenmetern, Kadenz mit Ø/Maximum oder Links-rechts-Leistungsbalance. Hauptwertglättung, Zonenfarben und Balance-Warnschwelle sind einstellbar.
- **Palmarès:** Fahrtbestwerte von 1 Sekunde bis 60 Minuten, als wechselndes Kompaktfeld oder Tabelle. Intervals.icu liefert Saison- und/oder 42-Tage-Referenzen. `*` bedeutet: Werte nur seit Neustart der Erweiterung, frühere Leistungen fehlen. `†` kennzeichnet Referenzen älter als 48 Stunden. Der Zeitstempel und der manuelle Abgleich stehen in den Einstellungen.
- **Réserve:** W′-Balance, theoretisch verfügbare Dauerleistung und Fahrtstatistik. Parameter kommen aus Intervals.icu (Rad-FTP/W′, gegebenenfalls Indoor-FTP) oder werden als CP/W′ manuell eingegeben. Deutsch und Englisch werden unterstützt.

Ultra ist noch nicht implementiert. Metrische Einheiten sind derzeit fest eingestellt.

## Installation und Updates

Ziel für neue Releases: [kEtape Downloads](https://github.com/jlojkasek/ketape/releases). Die Umstellung ist lokal vorbereitet, noch nicht veröffentlicht. Bisherige Versionen stehen weiterhin im [alten Downloadkanal](https://github.com/jlojkasek/ketape-releases/releases).

Auf dem **neuesten Karoo** kann der direkte GitHub-APK-Link über die Hammerhead Companion App geteilt werden. Auf **Karoo 2** ist dieser Companion-Weg nicht verfügbar; dort per ADB installieren. Siehe [Hammerhead-Anleitung](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading).

Unter kEtape die Einstellungen öffnen, anschließend Felder im Karoo-Fahrprofil hinzufügen. Die Editoransicht verwendet ausschließlich Beispieldaten. Ab 1.0.0 lautet das App-Paket `io.github.jlojkasek.ketape`; der Wechsel von Stacked Fields 0.x erfordert einmalig eine neue Installation, neue Feldplatzierungen und eine erneute Intervals-Verbindung. Updates innerhalb 1.x behalten Paket und Zertifikat bei.

Der Kandidat 1.1.2 verwendet das neue [Update-Manifest](https://github.com/jlojkasek/ketape/releases/latest/download/manifest.json). Diese Adresse wird erst nach der freigegebenen Veröffentlichung verfügbar. Der [bisherige Kanal](https://github.com/jlojkasek/ketape-releases/releases/latest/download/manifest.json) bleibt für installierte Versionen erhalten. Die Verteilung erfolgt ausschließlich über GitHub; keine Einreichung bei Awesome Karoo oder der offiziellen Extension Library ist vorgesehen.

## Terminal-first entwickeln

Voraussetzungen: Python 3, JDK 17 oder 21, Android Command-Line Tools. Android Studio ist optional.

```sh
export JAVA_HOME=/pfad/zum/jdk
export ANDROID_HOME=/pfad/zum/android-sdk
sdkmanager 'platform-tools' 'platforms;android-35' 'platforms;android-34' 'build-tools;35.0.0'
scripts/build.sh
```

Alternativ werden bestehende lokale Android-Studio-Pfade aus `local.properties` und `.gradle/config.properties` gelesen. Sie bleiben lokal. `ANDROID_SDK_ROOT` wird ebenfalls unterstützt.

**Ein Prüf-Befehl:** `scripts/build.sh` führt Python-Tests, Debug- und Release-Unit-Tests, Lint und beide APK-Builds aus. Zusätzliche Gradle-Argumente werden weitergereicht. Bei einem inkrementellen Kotlin-Cacheproblem nach Paketumzügen: `scripts/build.sh -Pkotlin.incremental=false`; kein Löschen von Quell- oder Nutzerdaten erforderlich.

```sh
scripts/install.sh                         # Debug-APK auf genau einem autorisierten Gerät
scripts/install.sh dist/v1.1.1/ketape-1.1.1.apk
```

Bei mehreren Geräten `ANDROID_SERIAL` setzen. Installiert wird mit `adb install -r`, ohne Deinstallation. Abweichende Signaturen werden von Android abgelehnt.

## Abhängigkeiten und Signing

Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Build Tools 35.0.0. Der Gradle-Download hat eine SHA-256-Prüfsumme. Abhängigkeiten werden durch Gradle-Lockdateien und `gradle/verification-metadata.xml` festgelegt. Kein `mavenLocal()`. Prüfsummen bei einem geplanten Dependency-Update bewusst prüfen, nicht zur Umgehung eines Fehlers blind regenerieren.

Karoo SDK 1.1.9 ist unter `vendor/karoo-ext` vendort. Herkunft, Commit und Apache-Lizenz stehen in `UPSTREAM.md` und `LICENSE`. Der dokumentierte Quellstand wurde am 25.11.2026 gegen upstream verglichen: 33 Dateien identisch. So sind keine GitHub-Packages-Zugangsdaten zum Bauen nötig.

Der bisherige Signierschlüssel liegt für lokale Releases nun explizit unter `.signing/release.keystore` (ignoriert, nur Eigentümerzugriff). Das Zertifikat bleibt unverändert. Die ursprüngliche lokale Kopie bleibt erhalten. **Eine unabhängige, verschlüsselte Offline-Sicherung muss der Eigentümer separat aufbewahren.** Die zweite lokale Kopie schützt nicht vor Geräteverlust. Der Schlüssel behält seine bisherigen Android-Debug-Zugangsdaten; entscheidend ist der geschützte Dateizugriff.

Ohne diesen Schlüssel baut CI mit einer eigenen Debug-Signatur auch die Release-Variante. Solche APKs sind keine Updates für veröffentlichte Installationen. Das Release-Skript blockiert ihre Verteilung durch Zertifikatsprüfung. Keine Signierschlüssel in GitHub Secrets nötig.

## Veröffentlichen

```sh
python3 scripts/release.py                 # nur lokal vorbereiten, kein Upload
python3 scripts/release.py --verify-only 1.1.0 --compatibility  # öffentlichen Bestand nur lesen/prüfen
```

Veröffentlichen benötigt ausdrücklich `--publish`, einen sauberen Quellstand, einen auf `origin` gesicherten passenden Tag und einen erfolgreichen aktuellen CI-Lauf auf demselben Commit. APK, Manifest, Prüfsummen und `build-info.json` enthalten eine nachvollziehbare Zuordnung. Vollständiger Ablauf: [Release-Anleitung](releases/README.md).

Die öffentliche 1.1.0 wird nicht überschrieben. Der lokal vorbereitete Migrationskandidat ist **1.1.2 (14)**; veröffentlicht ist weiterhin 1.1.1 (13). Die nicht abschließend rekonstruierte Herkunft des älteren APK ist im [Bestandsprotokoll](releases/AUDIT-2026-11-25.md) festgehalten.

## Daten, Modell und Kontinuität

Karoo liefert native Live-, Mittel-, Maximum- und NP-Werte. Leistung, Tempo und Kadenz verwenden für die Glättung native SDK-Streams; Puls wird lokal geglättet. Links-rechts-Balance liest explizit `PEDAL_POWER_BALANCE_LEFT`. Sensor-Hauptwerte werden nach fünf Sekunden ohne Update ausgeblendet. Zonen verwenden die Profilgrenzen, die Palette ist ein dokumentierter Fallback; ein bestätigtes Mapping kann mit `scripts/karoo_colors.py` erstellt werden.

Palmarès ermittelt gleitende Bestwerte aus sekündlich abgetasteter Leistung. Fehlende Samples und Pausen unterbrechen das laufende Fenster; bereits erfasste Bestwerte bleiben bis Fahrtende erhalten. Nach Prozessneustart fehlen frühere Bestwerte und werden nicht als vollständige Fahrt ausgegeben. Es werden keine zusätzlichen FIT-Felder geschrieben.

Réserve integriert Rohleistung mit gemessenem Zeitabstand. Oberhalb der Referenzleistung sinkt die Reserve linear, darunter erholt sie sich nach dem Differentialmodell. Negative Werte bleiben erhalten. Gültige 0 W bedeuten Erholung; Pause, fehlende Daten und Intervalle über 2,5 Sekunden frieren die Integration ein. Leistungsprognosen werden bei fehlenden Daten oder Pause ausgeblendet. Parameter bleiben innerhalb der Fahrt fest. Ein bewusst bestätigter Modellneustart setzt Reserve und Statistik auf den Anfang zurück, rekonstruiert aber keine fehlenden Daten.

Alle 15 Sekunden wird ein Zwischenstand gespeichert. Das SDK liefert keine stabile Fahrt-ID. Deshalb bleibt nach Service-Neustart eine Kontinuitätswarnung; ein maximal zehn Minuten alter Zwischenstand kann nicht zweifelsfrei einer Fahrt zugeordnet werden. Ohne verwendbaren Zwischenstand startet das Modell voll mit Warnung. Frühere Diagrammpunkte werden nicht wiederhergestellt. Historie und Rechenpuffer sind begrenzt.

Das Modell ist keine Messung der gesamten Ermüdung und keine Leistungsgarantie. Die analytische Integration unterscheidet sich geringfügig vom dokumentierten sekündlichen Euler-Verfahren von [Intervals.icu](https://forum.intervals.icu/t/w-and-cp-settings-help/1155?page=2). FTP-Import und manuelle CP sind bewusst getrennt benannt.

## Intervals.icu und Datenschutz

Persönlichen API-Schlüssel in Palmarès eingeben. Speicherung mit AES-GCM und Android Keystore; keine Schlüssel in Logs oder Backups. HTTPS mit Zeitlimits und ohne Redirects; Lesen von Profil, Rad-Bestwertkurven und Rad-Sporteinstellungen. Kein Schreibzugriff auf Trainingsdaten. Offline bleiben zuletzt erfolgreich gespeicherte Referenzen verfügbar. Trennen entfernt Schlüssel und Intervals-Caches; Widerruf erfolgt bei Intervals.icu. Manuelle Parameter und die lokale Fahrtmodellstatistik sind davon unabhängig.

## Tests und bekannte Grenzen

Automatisierte Prüfungen umfassen Modellmathematik, Sensorlücken, Pause/Fortsetzen, Neustart/Checkpoint, Intervals-Parser, Zonen, Balance, Android-Textmessung und Release-Schutzprüfungen. Aktuelle Ergebnisse stehen nach dem Build unter `app/build/reports/`; alte Testzahlen werden hier nicht als aktueller Nachweis weitergeführt.

`preview.html` ist eine Browser-Layoutstudie und kein Android-Test. Die tatsächlichen Editor-Vorschauen liegen im App-Code. Android-Instrumentierung: `./gradlew :app:connectedDebugAndroidTest`, nur auf bewusst ausgewähltem Gerät/Emulator. Die [Gerätecheckliste](releases/DEVICE-CHECKLIST.md) bleibt vor der Freigabe erforderlich.

Offen bleiben echte Karoo-Firmware-/Sensortests, alle Rastergrößen, Karoo-spezifisches Tag/Nacht-Verhalten und eine mehrstündige Akkuvergleichsmessung. Kleine Felder können bis zu sehr kleinen Schriftgrößen schrumpfen. Lint-Warnungen werden nicht pauschal unterdrückt. Native Systemfarben sind ohne verifiziertes Mapping nicht garantiert.
