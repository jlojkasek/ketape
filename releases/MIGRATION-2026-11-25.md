# Migration zu jlojkasek/ketape – Audit und Freigabeplan

Stand: 25.11.2026, Europe/Vienna. **Noch keine Freigabe zur Veröffentlichung.**
Nur lesende GitHub-Zugriffe; keine Sichtbarkeit, Releases, Tags, Secrets oder
Repository-Einstellungen geändert. Lokal sind Release-Werkzeug, Dokumentation und Migrationskandidat 1.1.2 (14)
vorbereitet. Paket, Schlüssel und Feld-IDs bleiben unverändert. Die ursprüngliche
Bestandsaufnahme unten bezieht sich auf den weiterhin unveränderten Remote-Stand.

## Verifizierter Bestand

| Bereich | Befund |
|---|---|
| Arbeitsstand | Anfangs sauber; main und origin/main sowie GitHub main auf `3b22a704fceb8a094d73afa2eef2dbc6316db6ef` |
| Remote | Nur origin: `https://github.com/jlojkasek/ketape.git` |
| Quellrepo | Privat, main ungeschützt, keine GitHub Releases, keine eigene Lizenz erkannt |
| Downloadrepo | Öffentlich, main ungeschützt, vier veröffentlichte Releases, keine Actions |
| Historie | 15 lokale Quellcommits; frische Mirror-Kopien beider Remote-Repositories geprüft; neun Quelltags |
| Tags | v1.1.1 ist annotiert; veröffentlichtes build-info nennt den aktuellen main-Commit. Kein Quelltag v1.1.0 vorhanden |
| AGENTS.md | Keine im Projekt oder in den direkt übergeordneten Verzeichnissen gefunden |
| GitHub-Secrets | Beide Repository-Secret-Listen leer; keine Environments. Keine Secretwerte abgefragt |
| Weitere Einstellungen | Quellrepo: keine Actions-Variablen, Webhooks, Deploy-Keys oder offenen PRs; keine Issues; 17 Actions-Caches |
| CI | 11 Läufe: zwei erfolgreich auf aktuellem HEAD, neun frühere Fehler; sämtliche Logs heruntergeladen |
| CI-Artefakte | Zwei ketape-debug-Artefakte, jeweils APK; heruntergeladen und Archivinhalt gescannt |
| Berechtigungen | Workflow contents:read; Standard-Token read; keine PR-Freigabe durch Actions. Actions per Commit gepinnt |

Historische CI-Fehler sind im bestehenden Stabilisierungsaudit dokumentiert;
die neuesten beiden Läufe sind erfolgreich. CI baut/testet Debug und Release,
führt Python-Tests und Lint aus und veröffentlicht lediglich ein Debug-Artefakt.
Es gibt keinen automatischen Produktionsrelease und keine CI-Schlüsselübergabe.

## Releases und Identität

Alle vier heruntergeladenen Releases: SHA256SUMS stimmen, APK-Signatur gültig,
Paket und Manifest-Version stimmen überein, identisches freigegebenes Zertifikat.

| Release | versionCode | Assets |
|---|---:|---|
| 1.0.0 | 10 | APK, manifest.json, SHA256SUMS |
| 1.0.1 | 11 | APK, manifest.json, SHA256SUMS |
| 1.1.0 | 12 | APK, manifest.json, SHA256SUMS |
| 1.1.1 | 13 | APK, manifest.json, SHA256SUMS, build-info.json |

- Paket: `io.github.jlojkasek.ketape`; Extension-ID: `ketape`.
- Zertifikat SHA-256: `1aa19d4c284fe6353076d6b0f3411fc3918ec9d1d20d226b4afe61682595a4ea`.
- Feld-IDs: `power`, `heart-rate`, `speed`, `distance`, `cadence`,
  `palmares-table`, `palmares-auto`, `reserve-balance`,
  `reserve-available-power`, `reserve-insights`.
- Bestehender Schlüssel: `.signing/release.keystore`, ignoriert, Dateirechte 600.
  Historische Debug-Schlüsselkonfiguration mit festem Standardpasswort;
  Passwortwerte werden hier nicht wiedergegeben. Die private Schlüsseldatei
  bleibt entscheidend. Kein Austausch oder Neugenerieren bei der Migration.
  Der Nutzer übernimmt die unabhängige Sicherung selbst; sie ist angekündigt,
  noch nicht bestätigt. Die restliche Vorbereitung wurde ausdrücklich freigegeben.
- Frühere 0.1.x-Quellen nutzten einen anderen Paketnamensraum. Diese Migration
  verspricht keine automatische Paketmigration von solchen Vorversionen.
- 1.1.0 hat weiterhin keinen belegten Quellstand. Downloadrepo-Tags zeigen auf
  Dokumentationscommits und dürfen nicht als Quelltags importiert werden.

## Sicherheitsprüfung und Grenzen

262 unterschiedliche Quellrepo-Blobs und drei Downloadrepo-Blobs über alle
Mirror-Referenzen geprüft: keine Schlüsseldateien, erkennbaren privaten Schlüssel,
GitHub-Tokens oder AWS-Zugangsschlüssel gefunden. Treffer der einfachen
Zuweisungsprüfung: bekannte Signier-Standardpasswörter und sechs historische
Varianten eines Preferences-Schlüsselnamens, keine eingebetteten Intervals-Tokens.
Elf CI-Logs und 894 Einträge aus zwei CI-APKs ohne Treffer für die geprüften
Schlüssel-/Tokenmuster. Keine geheimen Fundstelleninhalte im Protokoll.

Ergänzend wurde Gitleaks **8.30.1** aus dem offiziellen GitHub-Release lokal
verwendet (Archiv-Prüfsumme gegen die veröffentlichte Prüfsummenliste geprüft).
Beide vollständigen Mirror-Historien mit `--all --full-history`, Logs sowie alle
heruntergeladenen CI-/Release-Dateien mit Archivprüfung bis Tiefe 3 gescannt:
**alle drei Scans erfolgreich, null Funde**. Berichte und Ausgaben vollständig
redigiert; keine Werte ausgegeben. Das ist kein mathematischer Ausschluss jeder
möglichen sensiblen Information, schließt aber die bisher offene Scanner-Prüfung.
GitHub Secret Scanning ist aktuell deaktiviert.

**Verbleibendes Veröffentlichungsgate:** 17 GitHub-Actions-Caches wurden erneut
inventarisiert (Gradle-Abhängigkeiten, Wrapper, Transformationen und Gradle-Home).
Der Inhalt ist über den verwendeten lesenden API-Ablauf nicht heruntergeladen und
nicht gescannt. Vorschlag: diese reproduzierbaren Build-Caches nach Freigabe kurz
vor der öffentlichen Umstellung leeren und dann frisch aufbauen lassen. Die freigegebene Cache-Bereinigung ist unten protokolliert. Die gescannten Logs und
Artefakte können erhalten bleiben. Bei zwischenzeitlichen neuen Läufen/Commits
muss der Scan vor dem Public-Gate auf diesen Stand erweitert werden.

Historische Commit-Metadaten einschließlich Namen/E-Mail-Adressen bleiben sichtbar;
keine Historienumschreibung vorbereitet. Laut [GitHub-Dokumentation](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility)
werden beim Wechsel auch Actions-Historie und Logs öffentlich.

Audit-Rohdaten, Mirror-Repositories, Logs und Downloads liegen ausschließlich
unter dem bereits ignorierten `dist/migration-audit/`; nicht committen oder teilen.

## Update-Pfad und externe Abhängigkeiten

Bisherige öffentliche stabile URL (installierte Versionen bis 1.1.1):
`https://github.com/jlojkasek/ketape-releases/releases/latest/download/manifest.json`

Bisherige öffentliche APK-URLs:
`https://github.com/jlojkasek/ketape-releases/releases/download/v<VERSION>/ketape-<VERSION>.apk`

Der lokal vorbereitete Kandidat verwendet:
`https://github.com/jlojkasek/ketape/releases/latest/download/manifest.json`.
Diese Adresse ist noch nicht öffentlich verfügbar. `releases/channel.json` und
Android-Metadaten sind gemeinsam umgestellt. Der alte Kanal ist ausdrücklich als
`compatibilityRepository` konfiguriert. README und Release-Anleitung beschreiben
beide Kanäle und den noch unveröffentlichten Zustand.

Nutzerentscheidung: **ausschließlich eigenes GitHub-Repository**, vorerst keine
Einreichung bei Awesome Karoo oder im offiziellen Hammerhead-Dashboard. Diese
Einreichungen sind aus dem Arbeitsumfang entfernt. Die vorherige öffentliche
Recherche ergab keine Awesome-Karoo-/GitHub-Code-Suchtreffer; private Einträge
wurden nicht überprüft. Bestehende Update-Links bleiben unabhängig davon erhalten.
Die tatsächliche Übernahme der neuen APK-Metadaten durch Karoo ist noch am Gerät
zu testen; deshalb alten Kanal weiterhin pflegen.

Vendored Karoo SDK 1.1.9 hat Apache-2.0-Lizenz und Herkunftsnachweis; dessen
Lizenz/Notices erhalten. Es wurde keine eigene Projektlizenz hinzugefügt. Öffentliche Sichtbarkeit allein
ist keine allgemeine Nachnutzungslizenz; eine Lizenzwahl ist ein separater Schritt.

## Konkreter Ablauf mit Freigaben

1. **Audit abschließen, weiter privat:** Secret-Scan ist für den inventarisierten Stand abgeschlossen;
   vor Sichtbarkeitswechsel aktualisieren und Caches behandeln. Schlüsselbackup
   übernimmt der Nutzer. Keine externen Registrierungen vornehmen.
   Repository-Snapshot samt Tags, Releases und Hashes sichern. Bei echten
   Zugangsdatenfunden zuerst sperren/rotieren, gesonderte Bereinigung planen.
2. **Release-Werkzeug lokal erweitert:** Primärrepo und Kompatibilitätsrepo sind
   getrennt. Der Primärrelease prüft die Versionscodes aller stabilen Releases
   beider Kanäle vor Upload. Primär-Quelltag muss bereits existieren. Der separate
   Befehl `--publish-compatibility VERSION` verifiziert den öffentlichen
   Primärrelease samt Herkunft, lädt ihn herunter und übernimmt APK/build-info
   bytegleich. Nur Legacy-Manifest und Prüfsummen werden angepasst. Beide Kanäle
   hosten dieselbe signierte APK; kein neuer Build beim Spiegeln. Alle bisherigen
   Prüfungen auf Signatur, Paket, Quelltag, CI, Privatheit und unveränderliche
   veröffentlichte Releases bleiben aktiv. Fehler im zweiten Schritt lassen
   dessen alten Kanal intakt; Nachprüfung über `--verify-only VERSION --compatibility`.
3. **Lokale Brückenversion vorbereitet:** 1.1.2 mit versionCode 14. Android-URL,
   Kanal und README angepasst; bestehende IDs/Einstellungen unverändert. Vollständig
   lokal gebaut und signiert, noch nicht committed, gepusht, installiert oder
   veröffentlicht. Der Dirty-Build ist nur ein Testkandidat. Nach Commit/Tag und
   grüner CI muss der veröffentlichbare Build aus diesem sauberen Stand entstehen.
4. **Gerätetest:** reale Installation 1.1.1 mit vorhandenen Feldern/Einstellungen
   auf Brückenversion aktualisieren; Paket, Zertifikat und Daten erhalten.
   Geräteupdate über altes Manifest und danach neues Manifest testen. Vorab ist
   ein lokaler Installationsversuch möglich; vollständiger öffentlicher Kanaltest
   erst nach Public-Gate. Kein Downgrade als Rückfallstrategie voraussetzen.
5. **Explizites Public-Gate:** erst nach bestandenem Veröffentlichungsaudit den
   Nutzer konkret um Freigabe bitten, jlojkasek/ketape mit vollständiger Historie
   öffentlich zu machen und die geprüfte Brückenversion zu veröffentlichen.
   Fehlende eigene Lizenz und offene Testgrenzen dabei benennen. Ein späteres Private
   macht bereits heruntergeladene Quellen nicht rückholbar.
6. **Nach Freigabe:** Sichtbarkeit ändern, Brückenversion aus genau dem geprüften
   Quellcommit lokal bauen/signieren, via gh als Draft hochladen und zurücklesen.
   Primärrelease veröffentlichen und anonym alle Assets prüfen. ERST DANN den
   alten Kanal auf eine neue Brückenveröffentlichung setzen. Scheitert der zweite
   Schritt, bleibt dessen bisheriges 1.1.1 intakt; Schritt gezielt wiederholen.
7. **Historische Releases:** für das Lean-Ziel nicht zwingend kopieren; alte
   Downloads dürfen zunächst bleiben. Falls gewünscht APKs byteidentisch kopieren,
   nie neu signieren. Historische Manifeste verweisen weiterhin auf alte URLs;
   keine stillen Änderungen ihrer Checksummen. Für 1.1.0 keinen erfundenen
   Quelltag anlegen: getrennte Archivbeschreibung oder vorerst nur alter Kanal.
8. **Kompatibilitätsphase:** altes Repo samt URL weiter verfügbar und schreibbar
   halten, Dokumentation auf neues Repo verweisen lassen. Keine Dashboard-Einreichung.
   Künftige Releases solange über den alten Kanal
   anbieten, wie dort gebundene Nutzer/Registrierungen möglich sind. Keine feste
   Löschfrist und kein vorzeitiges Archivieren. Entfernen wäre separate Freigabe.

## Rückfall und Abnahmekriterien

Vor Veröffentlichung: lokale Änderungen verwerfen genügt. Nach Veröffentlichung:
alte Assets/URLs erhalten; Fehler durch neuen höheren versionCode mit identischer
Signatur korrigieren. Keine veröffentlichten APKs ersetzen, keine Tags verschieben.
Ein gescheiterter Legacy-Schritt darf das bereits erfolgreiche Primärrelease nicht
neu erzeugen. Quell-CI bleibt unverändert, Schlüssel bleiben ausschließlich lokal.

Neue lokale Tests sichern Paket/Zertifikat, bestehende Feld-IDs, konsistente
Update-Konfiguration, Konflikte zwischen beiden Kanälen, identische APK-Übernahme,
Herkunftsprüfung und Legacy-Nachprüfung.

Validierung des lokalen Kandidaten:
- 26 Python-Tests bestanden.
- Je 46 Android-Unit-Tests für Debug und Release, keine Fehler.
- Beide Builds erfolgreich; Lint je null Fehler und 25 bestehende Warnungen.
- APK-Paket, bestehendes Zertifikat, Version 1.1.2/14 und eingebettete neue URL geprüft.
- Kein Geräte-/End-to-End-Update-Test behauptet; neue CI erst nach späterem Push.
- Kandidat: `dist/v1.1.2/ketape-1.1.2.apk`, nur lokal, nicht veröffentlichungsfertig.

**Cache-Bereinigung:** Nach ausdrücklicher Freigabe alle 17 inventarisierten
Build-Caches gelöscht und null verbleibende Caches bestätigt. GitHub-main und
die elf bereits gescannten CI-Läufe sind unverändert. Der alte Kanal liefert
weiterhin 1.1.1.

**Nächste Freigabe:** Sichtbarkeit des Hauptrepos samt Historie und CI-Logs
auf öffentlich stellen und die Brückenversion nach grünem CI und Gerätetest
zuerst im Hauptrepo, dann über den alten Kanal veröffentlichen.
