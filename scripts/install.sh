#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
adb_bin="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if command -v adb >/dev/null 2>&1; then adb_bin=adb; fi
if [ ! -x "$adb_bin" ] && [ "$adb_bin" != adb ]; then
  echo 'ADB fehlt. Android SDK Platform Tools installieren.' >&2
  exit 1
fi
apk=app/build/outputs/apk/debug/app-debug.apk
if [ ! -f "$apk" ]; then echo 'APK fehlt. Zuerst scripts/build.sh ausführen.' >&2; exit 1; fi
"$adb_bin" devices
# adb fails safely if several devices are connected; use ANDROID_SERIAL to select one.
"$adb_bin" install -r "$apk"
"$adb_bin" shell am start -n de.jannes.stacked/.SettingsActivity
