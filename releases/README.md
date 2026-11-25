# Release-Ablauf

## Vorbereiten

1. Version und steigenden Code in `app/build.gradle.kts` setzen, öffentliche Hinweise in `releases/<Version>.md` schreiben. Eine veröffentlichte Version niemals wiederverwenden.
2. `scripts/build.sh` ausführen.
3. `python3 scripts/release.py --prepare-only --test-on-karoo` baut und installiert das signierte APK. Die [Gerätecheckliste](DEVICE-CHECKLIST.md) abarbeiten. Ohne Gerät kann mit `--prepare-only` nur lokal vorbereitet werden.
4. Änderungen reviewen, vollständig committen und den Versionstag auf denselben Commit setzen. Commit und Tag im Quellrepo sichern. Keine Schlüssel oder `.signing/` hinzufügen.
5. Den GitHub-Workflow für diesen Commit abwarten. Das Skript prüft den neuesten passenden Push-Lauf; ein älterer Erfolg überstimmt keinen aktuellen Fehler.

## Veröffentlichen

```sh
python3 scripts/release.py --publish --test-on-karoo
```

Das Skript prüft sauberes Git, Quelltag auf `origin`, CI, Paket, Version und das bestehende Zertifikat. Es baut beide Varianten, führt Python-/Android-Tests und Lint aus, erstellt APK, `manifest.json`, `build-info.json` und `SHA256SUMS`, lädt einen Entwurf hoch und liest dessen Dateien vor der Freigabe wieder zurück. Danach wird der Release auf `latest` geschaltet und ohne Anmeldung geprüft.

`--publish` ohne `--test-on-karoo` ist nur für einen bereits am identischen Quellstand abgeschlossenen Gerätetest gedacht. Ohne `--publish` oder `--publish-compatibility` erzeugt der Befehl ausschließlich lokale Dateien. `--setup` ist nur zusammen mit `--publish` für die erstmalige Einrichtung eines fehlenden öffentlichen Downloadrepos verfügbar.

Bei einem Fehler **nach** der Freigabe sagt die Ausgabe ausdrücklich, dass der Release bereits veröffentlicht ist (Exitcode 2). Dann nur die Nachprüfung wiederholen:

```sh
python3 scripts/release.py --verify-only 1.1.2
```

Dieser Befehl baut, installiert und veröffentlicht nichts. Er prüft versioniertes Manifest, Prüfsummen, APK-Paket, Version und Signatur. Er funktioniert auch für ältere Releases ohne `build-info.json`. Die stabile `latest`-Adresse wird zusätzlich unmittelbar nach einer Veröffentlichung geprüft; eine ältere Version muss später nicht mehr `latest` sein.

## Übergang zu einem Repository

Die lokale Konfiguration zeigt auf `jlojkasek/ketape`. Solange es privat ist,
blockiert das Skript die Veröffentlichung. Sichtbarkeit wird niemals automatisch
geändert. Audit und ausdrückliche Nutzerfreigabe sind vor der ersten öffentlichen
Umstellung erforderlich; siehe [Migrationsprotokoll](MIGRATION-2026-11-25.md).

`--publish` veröffentlicht zuerst nur im Hauptrepository. Der Versionscode muss
höher als in allen stabilen Releases beider Kanäle sein. Nach erfolgreicher
öffentlicher Prüfung wird dieselbe APK separat für alte Installationen angeboten:

```sh
python3 scripts/release.py --publish-compatibility 1.1.2
python3 scripts/release.py --verify-only 1.1.2 --compatibility
```

Der Kompatibilitätsschritt lädt die geprüften Primärdateien herunter, prüft den
sauberen Quellcommit/Tag und CI erneut und übernimmt APK und build-info bytegleich.
Nur Manifest-Downloadadresse und zugehörige Prüfsummen werden für das alte Repo
angepasst. Er baut und installiert nichts. Das APK enthält bereits die neue
Update-Adresse. Jeder Kanal hostet seine eigene Kopie derselben APK; dadurch
bleibt die bestehende Manifeststruktur erhalten.

Bei einem Fehler vor Veröffentlichung im alten Kanal bleibt dessen vorheriger
Release aktiv. Nach Behebung nur den Kompatibilitätsschritt wiederholen. Bereits
veröffentlichte Releases werden nicht überschrieben; falls der Schritt schon
veröffentlicht hat, ausschließlich `--verify-only ... --compatibility` verwenden.
Historische Releases prüfen, beispielsweise:

```sh
python3 scripts/release.py --verify-only 1.1.1 --compatibility
```

Keine Einreichung in externe Verzeichnisse. Das alte Repo nicht löschen oder
archivieren, solange es für Updates benötigt wird. Historische Downloads werden
vorerst nicht kopiert; insbesondere kein erfundener Quelltag für 1.1.0.

## Schlüssel und Wiederherstellung

Der bestehende Schlüssel liegt lokal unter `.signing/release.keystore`; die ursprüngliche Kopie wurde nicht gelöscht. Das öffentliche Zertifikat ist in `channel.json` festgelegt. Auf einem neuen Rechner exakt diesen Schlüssel aus einer unabhängigen sicheren Sicherung wiederherstellen, nicht einen neuen erzeugen. Lokale Dateirechte `700` für das Verzeichnis und `600` für die Datei verwenden.

Ein Build ohne lokalen Release-Schlüssel verwendet eine Wegwerfsignatur und wird vom Release-Skript blockiert. Die Release-APK ist unabhängig davon nicht debuggable. Kein Schlüssel wird an GitHub übertragen.

## Herkunft

`build-info.json` nennt Quellcommit, Dirty-Status, APK-Hash und Toolversionen. Ein Dirty-Build ist zum lokalen Testen erlaubt, nicht zum Veröffentlichen. Die Datei ist ein Herkunftsprotokoll, keine kryptografische Build-Attestierung. Bitidentische Builds über verschiedene JDKs/Betriebssysteme werden nicht behauptet.

Die ältere 1.1.0 hat keine vollständige belegte Quellzuordnung; siehe [Bestandsprotokoll](AUDIT-2026-11-25.md). Ab 1.1.1 soll die neue Quell-/CI-Prüfung diese Lücke verhindern.
