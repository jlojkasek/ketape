# Release-Ablauf

## Vorbereiten

1. Version und steigenden Code in `app/build.gradle.kts` setzen, öffentliche Hinweise in `releases/<Version>.md` schreiben. Eine veröffentlichte Version niemals wiederverwenden.
2. `scripts/build.sh` ausführen.
3. `python3 scripts/release.py --prepare-only --test-on-karoo` baut und installiert das signierte APK. Die [Gerätecheckliste](DEVICE-CHECKLIST.md) abarbeiten. Ohne Gerät kann mit `--prepare-only` nur lokal vorbereitet werden.
4. Änderungen reviewen, vollständig committen und den Versionstag auf denselben Commit setzen. Commit und Tag im privaten Quellrepo sichern. Keine Schlüssel oder `.signing/` hinzufügen.
5. Den GitHub-Workflow für diesen Commit abwarten. Das Skript prüft den neuesten passenden Push-Lauf; ein älterer Erfolg überstimmt keinen aktuellen Fehler.

## Veröffentlichen

```sh
python3 scripts/release.py --publish --test-on-karoo
```

Das Skript prüft sauberes Git, Quelltag auf `origin`, CI, Paket, Version und das bestehende Zertifikat. Es baut beide Varianten, führt Python-/Android-Tests und Lint aus, erstellt APK, `manifest.json`, `build-info.json` und `SHA256SUMS`, lädt einen Entwurf hoch und liest dessen Dateien vor der Freigabe wieder zurück. Danach wird der Release auf `latest` geschaltet und ohne Anmeldung geprüft.

`--publish` ohne `--test-on-karoo` ist nur für einen bereits am identischen Quellstand abgeschlossenen Gerätetest gedacht. Ohne `--publish` erzeugt der Befehl ausschließlich lokale Dateien. `--setup` ist nur zusammen mit `--publish` für die erstmalige Einrichtung eines fehlenden öffentlichen Downloadrepos verfügbar.

Bei einem Fehler **nach** der Freigabe sagt die Ausgabe ausdrücklich, dass der Release bereits veröffentlicht ist (Exitcode 2). Dann nur die Nachprüfung wiederholen:

```sh
python3 scripts/release.py --verify-only 1.1.1
```

Dieser Befehl baut, installiert und veröffentlicht nichts. Er prüft versioniertes Manifest, Prüfsummen, APK-Paket, Version und Signatur. Er funktioniert auch für ältere Releases ohne `build-info.json`. Die stabile `latest`-Adresse wird zusätzlich unmittelbar nach einer Veröffentlichung geprüft; eine ältere Version muss später nicht mehr `latest` sein.

## Schlüssel und Wiederherstellung

Der bestehende Schlüssel liegt lokal unter `.signing/release.keystore`; die ursprüngliche Kopie wurde nicht gelöscht. Das öffentliche Zertifikat ist in `channel.json` festgelegt. Auf einem neuen Rechner exakt diesen Schlüssel aus einer unabhängigen sicheren Sicherung wiederherstellen, nicht einen neuen erzeugen. Lokale Dateirechte `700` für das Verzeichnis und `600` für die Datei verwenden.

Ein Build ohne lokalen Release-Schlüssel verwendet eine Wegwerfsignatur und wird vom Release-Skript blockiert. Die Release-APK ist unabhängig davon nicht debuggable. Kein Schlüssel wird an GitHub übertragen.

## Herkunft

`build-info.json` nennt Quellcommit, Dirty-Status, APK-Hash und Toolversionen. Ein Dirty-Build ist zum lokalen Testen erlaubt, nicht zum Veröffentlichen. Die Datei ist ein Herkunftsprotokoll, keine kryptografische Build-Attestierung. Bitidentische Builds über verschiedene JDKs/Betriebssysteme werden nicht behauptet.

Die ältere 1.1.0 hat keine vollständige belegte Quellzuordnung; siehe [Bestandsprotokoll](AUDIT-2026-11-25.md). Ab 1.1.1 soll die neue Quell-/CI-Prüfung diese Lücke verhindern.
