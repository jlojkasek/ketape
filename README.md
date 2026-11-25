# kEtape für Hammerhead Karoo

Zeige deine wichtigsten Fahrwerte auf weniger Platz an, vergleiche deine Leistung mit deinen Bestwerten und behalte deine W′-Reserve im Blick.

**[Aktuelle APK herunterladen](https://github.com/jlojkasek/ketape/releases/latest)**

## Datenfelder

- **Stacked Fields** kombiniert Leistung, Puls, Tempo, Strecke oder Kadenz mit passenden Zusatzwerten wie Durchschnitt, Maximum, Normalized Power oder Höhenmetern. Zonenfarben und Glättung lassen sich einstellen.
- **Palmarès** zeigt Leistungsbestwerte aus der laufenden Fahrt als Kompaktfeld oder Tabelle. Mit Intervals.icu kannst du sie mit deinen Saisonbestwerten oder den letzten 42 Tagen vergleichen.
- **Réserve** schätzt, wie viel deiner W′-Reserve noch verfügbar ist. Du kannst deine Radparameter aus Intervals.icu übernehmen oder CP und W′ manuell eingeben.

Die App unterstützt Deutsch und Englisch. Die Datenfelder verwenden metrische Einheiten.

## Installation und Updates

1. Lade die APK aus dem [aktuellen Release](https://github.com/jlojkasek/ketape/releases/latest) herunter. Bei einem Karoo mit Companion-Sideloading kannst du den direkten APK-Link über die [Hammerhead Companion App](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading) teilen. Beim Karoo 2 erfolgt die Installation per ADB.
2. Öffne kEtape auf dem Karoo und richte bei Bedarf Intervals.icu oder Réserve ein.
3. Füge die gewünschten Felder im Karoo-Fahrprofil zu deinen Datenseiten hinzu.

Installiere Updates über die bestehende App, damit Einstellungen und Feldplatzierungen erhalten bleiben. Das [Update-Manifest](https://github.com/jlojkasek/ketape/releases/latest/download/manifest.json) verweist auf die aktuelle APK.

## Intervals.icu und Offline-Nutzung

Verbinde dein Konto in den Palmarès-Einstellungen mit einem persönlichen API-Schlüssel. Der Schlüssel wird verschlüsselt auf dem Karoo gespeichert. kEtape liest die benötigten Referenzwerte und Radparameter. Es schreibt keine Trainingsdaten zu Intervals.icu zurück.

Bei vorhandener Internetverbindung aktualisiert kEtape fällige Palmarès-Referenzen im Leerlauf. Unterwegs stehen die zuletzt geladenen Werte auch offline zur Verfügung. Den letzten Abgleich siehst du in den Palmarès-Einstellungen.

Réserve ist eine Schätzung. Der Wert hilft dir bei der Einteilung deiner Leistung, kann aber deine tatsächliche Ermüdung nicht messen.
