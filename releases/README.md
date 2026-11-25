# Karoo-Release

Erhöhe `versionName` und `versionCode` in `app/build.gradle.kts` und schreibe die öffentlichen Versionshinweise in `releases/<versionName>.md`. Committe die Änderungen im privaten Quellrepo. Für Version 0.1.3 lautet der Ablauf danach:

```sh
git tag v0.1.3
python3 scripts/release.py
```

Das Skript führt Python- und Android-Unit-Tests sowie Lint aus, baut und signiert die Release-APK und prüft Paket, Version und Zertifikat. Es veröffentlicht APK, Update-Manifest, Prüfsummen und Versionshinweise über einen GitHub-Entwurf im öffentlichen Download-Repo und prüft Manifest und APK anschließend ohne Anmeldung. Eine bereits veröffentlichte Version kann nicht überschrieben werden. Sichere Commit und Tag zusätzlich im privaten Quellrepo.

Für einen Gerätetest vor der Freigabe starte denselben Befehl mit `--test-on-karoo` und schließe den Karoo per USB mit aktiviertem USB-Debugging an. Das Skript installiert die gerade gebaute APK und fragt danach nach Freigabe. Für einen rein lokalen Build nutze `--prepare-only`. Die Dateien liegen in `dist/v<versionName>/`.

## Einmalige Einrichtung

Benötigt werden Python 3, Git, Java und das Android SDK. Für die Veröffentlichung kommt die GitHub CLI mit einmaligem `gh auth login` dazu. Der Gradle-Wrapper liegt im Repo. Das öffentliche Download-Repo ist bereits eingerichtet.

Gradle verwendet für den Release-Build denselben lokalen Keystore wie für die bisherigen Debug-APKs. Die Release-APK ist nicht debuggable. Sichere den bestehenden Android-Debug-Keystore separat. Das Skript gleicht das Zertifikat mit dem von 0.1.2 ab, damit ein Build mit einem anderen lokalen Schlüssel nicht versehentlich veröffentlicht wird. Ein direkter Wechsel auf einen neuen Schlüssel würde eine Neuinstallation erfordern.

## Update auf dem Karoo

Installationen ab 0.1.2 kennen das [Update-Manifest](https://github.com/jlojkasek/karoo-stacked-fields-releases/releases/latest/download/manifest.json). Ältere Installationen brauchen einmalig ein manuelles Update. Den vollständigen Updatepfad testest du auf einem Karoo, der noch die ältere Version installiert hat, sobald eine neuere Version öffentlich ist.
