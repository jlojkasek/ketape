# kEtape veröffentlichen

1. Version und Versionscode in `app/build.gradle.kts` erhöhen. Unter `releases/<Version>.md` kurz beschreiben, was sich für Karoo-Nutzer ändert.
2. Bei relevanten Neuerungen im [Projekt-README](../README.md#whats-new) unter „What's new“ einen Eintrag für die Version ergänzen. Neue Felder im passenden Funktionsabschnitt erklären und betroffene Grafiken mit Beispieldaten in `docs/images/` aktualisieren.
3. Den signierten Build mit `python3 scripts/release.py --prepare-only --test-on-karoo` auf dem Karoo prüfen. Die [Checkliste](DEVICE-CHECKLIST.md) umfasst die Gerätetests.
4. Den Commit auf `main` pushen und den erfolgreichen GitHub-Check für diesen Commit abwarten. Anschließend den Versionstag auf denselben Commit setzen und pushen.
5. Einmal mit `python3 scripts/release.py --publish` veröffentlichen.

Das Skript baut und signiert lokal, lädt die Dateien als Entwurf in `jlojkasek/ketape` und prüft anschließend den öffentlichen Download. Den vorhandenen Signierschlüssel ausschließlich lokal und in einer unabhängigen geschützten Sicherung aufbewahren.

Für eine erneute Prüfung eines bereits veröffentlichten Releases genügt `python3 scripts/release.py --verify-only <Version>`.

Ältere Downloads bleiben im [Archiv](https://github.com/jlojkasek/ketape-releases/releases) verfügbar.
