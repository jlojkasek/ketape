# Cadence / pedals

## Nutzung (Deutsch)

Unter Trittfrequenz stehen Classic, Performance und Dynamics zur Wahl. Classic
bleibt Standard und behält Ø/Max sowie die vorhandene optionale Balance-Einstellung.
Balance wird immer als links/rechts gezeigt, etwa 48/52. Performance ergänzt unten
den Mittelwert der Trittgleichmäßigkeit (PS) aus beiden Seiten. Dynamics zeigt Balance im Header sowie TE % und PS % unten.
Die Kadenz-Glättung bleibt unverändert einstellbar; Balance nutzt
native 3s-Mittelwerte. Ein ! ergänzt eine aktivierte Balance-Warnung unabhängig
von der Warnfarbe. Fehlende Daten erscheinen als Strich, gültige Nullen bleiben
sichtbar. Ein reiner Kadenzsensor funktioniert weiterhin ohne Pedaldaten.

## Usage (English)

Choose Classic, Performance or Dynamics under cadence. Classic remains the default
and preserves average/max and the existing optional balance setting. Balance always
reads left/right, e.g. 48/52. Performance adds the mean of both Pedal Smoothness (PS) readings below cadence. Dynamics
places balance in the header, with TE % and PS % below. The existing cadence smoothing
setting is retained; balance uses native 3s averages. An ! supplements an
enabled balance warning independently of its color. Missing data appears as a dash;
valid zeros remain visible. A cadence-only sensor still works without pedal data.

## SDK contract and limitations

Checked against the vendored Karoo Extensions SDK **1.1.9**, upstream commit
`f79f103cd0e1a9808db64474796b30d822cc0b17` (see `vendor/karoo-ext/UPSTREAM.md`).
The installed app consumes Karoo streams, not raw ANT packets.

| Stream | Explicit fields | Display / handling |
| --- | --- | --- |
| `CADENCE` / configured smoothed cadence | single cadence value | rpm; existing selection preserved |
| `SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE` | `PEDAL_POWER_BALANCE_LEFT` | left percent, rounded once; right = 100 − rounded left |
| `TORQUE_EFFECTIVENESS` | `TORQUE_EFFECTIVENESS_LEFT`, `TORQUE_EFFECTIVENESS_RIGHT` | percent, independent validated and rounded sides |
| `PEDAL_SMOOTHNESS` | `PEDAL_SMOOTHNESS_LEFT`, `PEDAL_SMOOTHNESS_RIGHT` | percent, independent validated and rounded sides |

`DataType.kt` explicitly names the optional left balance field; there is no native
right balance field in this SDK. The complement is only calculated after validating
a native left share in 0…100. TE/PS must be finite and within 0…100; they are never
complemented or mirrored. Performance averages the two valid PS readings before rounding. It displays a dash if either side is unavailable. A missing field or
non-streaming event clears its value, and each complete stream snapshot expires after
5 seconds without updates. A stream containing only POWER cannot create pedal data.

The SDK does not document a sensor/model compatibility matrix or decode ANT+ itself.
Karoo firmware and the paired sensor determine which optional fields are available.
ANT+ supports TE/PS; the sensor must actually transmit them. No Bluetooth equivalence
or support for every Favero/Garmin model is assumed. Raw ANT+ PS can also be combined;
this implementation reads only the SDK's explicitly named LEFT/RIGHT fields, never a
combined/single-value fallback. Whether firmware correctly omits unsupported or combined
values must still be checked with the actual pedal models. No capability flag for
combined PS is exposed by these SDK streams.

References:
- [Hammerhead data-field definitions and smoothing](https://support.hammerhead.io/hc/en-us/articles/35533240795419-Data-Fields-Legend)
- [Garmin ANT+ TE/PS semantics, percent units and combined PS](https://developer.garmin.com/connect-iq/api-docs/Toybox/AntPlus/TorqueEffectivenessPedalSmoothness.html)
- [Garmin torque-effectiveness ANT+ requirement](https://support.garmin.com/en-GB/?faq=y2aLFo6Clu1PNvHK6eTm98&searchType=noProduct)

## Verification

Unit tests cover sources, default selection, rounding, invalid/missing values, one-sided
samples, valid zeros, expiry and preview variation. Android layout tests cover all three
variants, reused views, missing values and 100/100 extremes at widths 120/160/280 dp and
heights 64/100/160 dp. Previews cycle through bilateral, one-sided, zero and absent data,
including zero and absent cadence. Device tests render the production RemoteViews on Karoo.

Remaining riding checks: compare native balance and TE/PS against the same sensors
in Karoo's built-in fields; verify Favero/Garmin ANT+ model behavior, combined-PS handling,
cadence-only sensors, reconnects, coasting, and firmware-specific unavailable sentinels.

To run instrumentation against the release-signed app without changing repository
build settings, use a temporary Gradle init script setting `android.testBuildType =
'release'` inside `plugins.withId('com.android.application')`, build
`:app:assembleReleaseAndroidTest`, install the app and test APKs with `adb install -r`,
then run `adb shell am instrument -w
io.github.jlojkasek.ketape.test/androidx.test.runner.AndroidJUnitRunner`.
