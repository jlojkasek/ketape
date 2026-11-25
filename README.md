# kEtape for Hammerhead Karoo

See your key ride metrics in less space, compare your power with your best efforts, and keep track of your remaining W′.

**[Download the latest APK](https://github.com/jlojkasek/ketape/releases/latest)**

## Data fields

- **Stacked Fields** pairs power, heart rate, speed, distance, or cadence with relevant metrics such as averages, maximums, Normalized Power, or elevation gain. You can adjust zone colors and smoothing.
- **Palmarès** shows your best power efforts from the current ride in a compact field or table. Connect Intervals.icu to compare them with your season bests, your bests from the last 42 days, or both.
- **Réserve** estimates how much W′ you have left. You can import your cycling parameters from Intervals.icu or enter critical power (CP) and W′ manually.

The app supports English and German. Data fields use metric units.

## Installation and updates

1. Download the APK from the [latest release](https://github.com/jlojkasek/ketape/releases/latest). On a Karoo that supports Companion sideloading, share the direct APK link with the [Hammerhead Companion app](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading). On Karoo 2, install the APK using ADB.
2. Open kEtape on your Karoo. Set up Intervals.icu or Réserve if you plan to use those features.
3. Edit the data pages in your Karoo ride profile and add the kEtape fields you want.

Install updates over your existing copy to keep your settings and field layouts. The [update manifest](https://github.com/jlojkasek/ketape/releases/latest/download/manifest.json) points to the current APK.

## Intervals.icu and offline use

Connect your account in the **General** tab using a personal API key. kEtape stores the key encrypted on your Karoo and reads the reference values and cycling parameters it needs. It does not write training data back to Intervals.icu.

When your Karoo is online and idle, kEtape refreshes Palmarès reference values if an update is due. The most recently downloaded values are still available offline. You can see when they were last refreshed in the Palmarès tab.

Réserve provides an estimate to help you pace your effort. It does not measure your actual fatigue.
