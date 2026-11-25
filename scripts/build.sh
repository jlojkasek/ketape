#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -f .gradle/config.properties ]; then
  studio_java=$(sed -n 's/^java.home=//p' .gradle/config.properties)
  if [ -x "$studio_java/bin/java" ]; then export JAVA_HOME="$studio_java"; fi
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! java -version >/dev/null 2>&1; then
  echo 'Java fehlt. Projekt einmal in Android Studio synchronisieren oder JAVA_HOME auf JDK 17/21 setzen.' >&2
  exit 1
fi
if [ ! -f local.properties ] && [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo 'Android SDK fehlt: sdk.dir in local.properties oder ANDROID_HOME setzen.' >&2
  exit 1
fi
python3 -m unittest discover -s scripts/tests
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug \
  :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease "$@"
