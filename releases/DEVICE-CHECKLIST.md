# Karoo-Freigabetest

Status: **noch nicht auf Hardware abgeschlossen**. Automatisierte Tests ersetzen diesen Nachweis nicht.

Protokollieren: Quellcommit, APK-SHA256, Version, Karoo-Modell/Firmware, Sensor, Datum und Prüfer. Keine API-Schlüssel notieren.

- [ ] Update über bestehendes 1.x ohne Deinstallation: Einstellungen und Intervals-Verbindung erhalten.
- [ ] Alle verwendeten Feldraster: Stacked Fields, beide Palmarès-Felder, alle drei Réserve-Felder. Kleine und große Zahlen, Deutsch/Englisch, Hell/Dunkel, Ausrichtung, Ränder, vergrößerte Systemschrift prüfen.
- [ ] Editor-Vorschau enthält nur Beispieldaten; reale Fahrt nutzt Sensorwerte.
- [ ] Live-Leistung, Fahrt-Ø, NP, Puls, Tempo, Distanz/Höhenmeter und L/R mit nativen Feldern vergleichen.
- [ ] Glättung ändern und Feld erneut öffnen; Balance live ein-/ausschalten.
- [ ] Aufnahme → manuelle Pause → Fortsetzen → Auto-Pause → Fortsetzen → Ende → neue Fahrt.
- [ ] Sensor aus/an und Wechsel der Leistungsquelle: keine erfundene Erholung; Prognosen bei Datenlücke verschwinden.
- [ ] Seitenwechsel: keine Rücksetzung von Réserve oder Fahrtbestwerten.
- [ ] Extension-Prozess während Aufnahme neu starten: Palmarès `*`, Réserve ⚠; keine Behauptung vollständiger Fahrtkontinuität.
- [ ] Modellneustart bewusst bestätigen: Statistik beginnt neu, gesperrte Fahrtparameter bleiben erhalten.
- [ ] Offline starten, Intervals-Fehler und erneuter Abgleich; alte Referenzen bleiben verfügbar, `†` bei mehr als 48 Stunden.
- [ ] Indoor-Profil vor Fahrtbeginn auswählen und importierte Referenz prüfen.
- [ ] APK/Manifest über den öffentlichen Weg prüfen; nach Veröffentlichung Updateangebot auf Karoo prüfen.

## Akkuvergleich

Je eine mehrstündige Fahrt mit und ohne kEtape bei vergleichbarer Dauer, Displayhelligkeit, Navigation, Funkverbindungen und Sensoren durchführen. Start-/Endakkustand, Temperaturen und aktive Felder notieren. Einzelne kurze Läufe sind kein belastbarer Verbrauchsnachweis. Bis dahin ist keine prozentuale Akku-Aussage freigegeben.
