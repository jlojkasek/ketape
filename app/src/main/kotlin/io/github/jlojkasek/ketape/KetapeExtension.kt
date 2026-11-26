package io.github.jlojkasek.ketape

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.RemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.*
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType.IMPERIAL
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private data class Metric(val type: String, val format: Format = Format.INTEGER)
internal enum class Format {
    INTEGER, SPEED, DISTANCE, ELEVATION, RATIO, DRIFT;

    fun unit(preferred: PreferredUnit?): String = when (this) {
        SPEED -> if (preferred?.distance == IMPERIAL) "mph" else "km/h"
        DISTANCE -> if (preferred?.distance == IMPERIAL) "mi" else "km"
        ELEVATION -> if (preferred?.elevation == IMPERIAL) "ft" else "m"
        else -> ""
    }
}
private data class Stack(val id: String, val titleRes: Int, val unit: String?,
    val main: Metric, val left: Metric, val right: Metric?,
    val leftLabel: String = "Ø", val rightLabel: String = "MAX", val mainIsRideSummary: Boolean = false)

private val stacks = listOf(
    Stack("power", R.string.field_0, "W", Metric(DataType.Type.POWER), Metric(DataType.Type.AVERAGE_POWER), Metric(DataType.Type.NORMALIZED_POWER), rightLabel = "NP"),
    Stack("heart-rate", R.string.field_3, "bpm", Metric(DataType.Type.HEART_RATE), Metric(DataType.Type.AVERAGE_HR), Metric(DataType.Type.MAX_HR)),
    Stack("speed", R.string.field_4, null, Metric(DataType.Type.SPEED, Format.SPEED), Metric(DataType.Type.AVERAGE_SPEED, Format.SPEED), Metric(DataType.Type.MAX_SPEED, Format.SPEED)),
    Stack("distance", R.string.field_5, null, Metric(DataType.Type.DISTANCE, Format.DISTANCE),
        Metric(DataType.Type.ELEVATION_GAIN, Format.ELEVATION), Metric(DataType.Type.ELEVATION_LOSS, Format.ELEVATION), "↗", "↘"),
    Stack("endurance", R.string.endurance_field, "NP/HR",
        Metric("endurance-ratio", Format.RATIO), Metric("endurance-drift", Format.DRIFT),
        Metric("endurance-z2"), "Drift", "Z2♥"),
    Stack("ride-load", R.string.ride_load_field, "TSS",
        Metric(DataType.Type.TRAINING_STRESS_SCORE), Metric(DataType.Type.CALORIES),
        Metric(DataType.Type.INTENSITY_FACTOR, Format.RATIO), "kcal", "IF", mainIsRideSummary = true),
    Stack("cadence", R.string.field_6, "rpm", Metric(DataType.Type.CADENCE), Metric(DataType.Type.AVERAGE_CADENCE), Metric(DataType.Type.MAX_CADENCE))
)

class KetapeExtension : KarooExtension("ketape", BuildConfig.VERSION_NAME) {
    private val system by lazy { KarooSystemService(this) }
    private val recordMonitor by lazy { RideRecordMonitor(this, system) }
    override val types: List<DataTypeImpl> by lazy { stacks.map { StackedType(this, system, it, recordMonitor) } +
            BestComparisonType(recordMonitor) +
            PowerBestType(recordMonitor) + ReserveField.entries.map { ReserveType(it, recordMonitor.reserve) } }

    override fun onCreate() {
        super.onCreate()
        system.connect()
        recordMonitor.start()
    }

    override fun onDestroy() {
        recordMonitor.stop()
        system.disconnect()
        super.onDestroy()
    }
}

private class StackedType(
    private val context: Context,
    private val system: KarooSystemService,
    private val stack: Stack,
    private val recordMonitor: RideRecordMonitor
) :
    DataTypeImpl("ketape", stack.id) {

    private val isEndurance = stack.id == "endurance"

    private fun mainSmoothingSeconds(): Int =
        context.getSharedPreferences("stacked", Context.MODE_PRIVATE).getInt(smoothingKey(stack.id), 1)
            .takeIf { it in smoothingOptions } ?: 1

    private fun subscribeMain(onState: (StreamState, Double?) -> Unit): String {
        val seconds = mainSmoothingSeconds()
        val source = smoothedSource(stack.id, seconds, stack.main.type)
        val pulseSmoother = if (stack.id == "heart-rate" && seconds > 1) PulseSmoother(seconds) else null
        return system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(source)) { event ->
            val value = (event.state as? StreamState.Streaming)?.dataPoint?.singleValue
                ?.takeIf { it.isFinite() && it >= 0.0 }
            onState(event.state, if (pulseSmoother == null) value
                else pulseSmoother.update(SystemClock.elapsedRealtime(), value))
        }
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        if (isEndurance) {
            val job = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    val ratio = recordMonitor.enduranceRatio
                    emitter.onNext(ratio?.let {
                        StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to it)))
                    } ?: StreamState.NotAvailable)
                    delay(1000)
                }
            }
            emitter.setCancellable { job.cancel() }
            return
        }
        val id = subscribeMain { state, value ->
            emitter.onNext(if (state is StreamState.Streaming) {
                if (value != null) StreamState.Streaming(
                    DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value))
                ) else StreamState.NotAvailable
            } else state)
        }
        emitter.setCancellable { system.removeConsumer(id) }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val values = ConcurrentHashMap<String, Double>()
        val timestamps = ConcurrentHashMap<String, Long>()
        val metrics = listOfNotNull(stack.left, stack.right)
        val isPower = stack.id == "power"
        val supportsZones = isPower || stack.id == "heart-rate"
        val zoneRanges = AtomicReference<List<ZoneRange>>(emptyList())
        val profileId = if (supportsZones && !config.preview) {
            system.addConsumer<UserProfile> { profile ->
                zoneRanges.set((if (isPower) profile.powerZones else profile.heartRateZones)
                    .map { ZoneRange(it.min, it.max) })
            }
        } else null
        fun storeValue(type: String, value: Double?) {
            if (value != null && value.isFinite() && value >= 0.0) {
                values[type] = value
                timestamps[type] = SystemClock.elapsedRealtime()
            } else {
                values.remove(type)
                timestamps.remove(type)
            }
        }
        val ids = if (config.preview || isEndurance) emptyList() else {
            val main = subscribeMain { _, value -> storeValue(stack.main.type, value) }
            listOf(main) + metrics.map { it.type }.distinct().map { type ->
                system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(type)) { event ->
                    storeValue(type, (event.state as? StreamState.Streaming)?.dataPoint?.singleValue)
                }
            }
        }
        val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
        val pedalSamples = PedalSamples()
        val pedalIds = mutableMapOf<String, String>()
        val backgroundDrawable = if (config.boundariesEnabled) {
            R.drawable.field_background_rounded
        } else {
            android.R.color.white
        }
        scope.launch {
            // Load once per view activation, not in the one-second render loop.
            val systemColors = KarooPalette.load(context)
            var previewTick = 0
            var cachedIcon: Bitmap? = null
            var cachedIconColor: Int? = null
            try {
                while (isActive) {
                    val previewFrame = PreviewValues.frame(stack.id, previewTick)
                    val fg = context.getColor(R.color.foreground)
                    val bg = context.getColor(R.color.background)
                    val view = RemoteViews(context.packageName, R.layout.stacked)
                    val gravity = when (config.alignment) {
                        ViewConfig.Alignment.LEFT -> android.view.Gravity.LEFT
                        ViewConfig.Alignment.RIGHT -> android.view.Gravity.RIGHT
                        else -> android.view.Gravity.CENTER_HORIZONTAL
                    } or android.view.Gravity.CENTER_VERTICAL
                    view.setInt(R.id.main_row, "setGravity", gravity)
                    // Align the complete group, never the number independently from its unit.
                    listOf(R.id.main, R.id.unit).forEach {
                        view.setInt(it, "setGravity", android.view.Gravity.CENTER)
                    }
                    // The compact main group is measured below; footer cells keep native autosizing.
                    val endurance = recordMonitor.enduranceSnapshot
                    fun rawValue(metric: Metric, index: Int): Double? =
                        if (isEndurance) {
                            if (config.preview) when (index) {
                                0 -> 1.42 + (previewTick % 5) / 100.0
                                1 -> 2.1 + (previewTick % 5) / 10.0
                                else -> 184.0 + previewTick % 5
                            } else when (index) {
                                0 -> recordMonitor.enduranceRatio
                                1 -> endurance.drift
                                else -> endurance.zoneTwoPower
                            }
                        } else
                        if (config.preview) when (index) {
                            0 -> if (stack.id == "cadence") PreviewValues.cadence(previewTick) else previewFrame.main
                            1 -> previewFrame.average
                            else -> previewFrame.secondary
                        } else {
                            val timestamp = timestamps[metric.type]
                            // Never leave a disconnected sensor's last current value on screen.
                            if (metric == stack.main && !stack.mainIsRideSummary &&
                                timestamp != null && SystemClock.elapsedRealtime() - timestamp > 5000) null
                            else values[metric.type]
                        }
                    val mainValue = rawValue(stack.main, 0)
                    val ranges = if (config.preview && supportsZones) {
                        if (isPower) listOf(ZoneRange(0, 165), ZoneRange(166, 225), ZoneRange(226, 270),
                            ZoneRange(271, 315), ZoneRange(316, 360), ZoneRange(361, 450), ZoneRange(451, 2000))
                        else listOf(ZoneRange(0, 110), ZoneRange(111, 135), ZoneRange(136, 155),
                            ZoneRange(156, 175), ZoneRange(176, 220))
                    } else zoneRanges.get()
                    val colorMode = prefs.getString("zone_colors", "background")
                    val zone = if (supportsZones && colorMode != "off")
                        ZoneColors.resolve(mainValue, ranges, isPower,
                            if (isPower) systemColors.power else systemColors.heart) else null
                    val backgroundZone = zone?.takeIf { colorMode == "background" }
                    val badgeZone = zone?.takeIf { colorMode == "badge" }
                    val pedalLayout = if (stack.id == "cadence")
                        PedalLayout.from(prefs.getString(PEDAL_LAYOUT_KEY, null)) else PedalLayout.CLASSIC
                    val showBalance = stack.id == "cadence" &&
                        (pedalLayout != PedalLayout.CLASSIC || prefs.getBoolean("cadence_balance", false))
                    if (!config.preview && stack.id == "cadence") {
                        val sources = pedalSources(pedalLayout, showBalance)
                        (pedalIds.keys - sources).forEach { type ->
                            pedalIds.remove(type)?.let(system::removeConsumer)
                            pedalSamples.update(type, null, SystemClock.elapsedRealtime())
                        }
                        (sources - pedalIds.keys).forEach { type ->
                            pedalIds[type] = system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(type)) { event ->
                                pedalSamples.update(type, (event.state as? StreamState.Streaming)?.dataPoint?.values,
                                    SystemClock.elapsedRealtime())
                            }
                        }
                    }
                    val pedals = if (config.preview) PreviewValues.pedals(previewTick)
                        else pedalSamples.snapshot(SystemClock.elapsedRealtime())
                    val balance = if (showBalance) pedals.balance else null
                    val balanceWarning = exceedsBalanceThreshold(balance, prefs.getInt(BALANCE_WARNING_KEY, 0))
                    val contentColor = if (balanceWarning) Color.WHITE else backgroundZone?.foreground ?: fg
                    val fieldColor = if (balanceWarning) Color.rgb(185, 28, 28) else backgroundZone?.background ?: bg
                    view.setImageViewResource(R.id.field_background, backgroundDrawable)
                    view.setInt(R.id.field_background, "setColorFilter", fieldColor)
                    listOf(R.id.main, R.id.unit, R.id.left, R.id.right, R.id.pedal_header).forEach {
                        view.setTextColor(it, contentColor)
                    }
                    view.setInt(R.id.zone, "setBackgroundColor", badgeZone?.background ?: Color.TRANSPARENT)
                    view.setTextColor(R.id.zone, badgeZone?.foreground ?: fg)
                    view.setViewVisibility(R.id.zone, if (badgeZone != null) android.view.View.VISIBLE else android.view.View.GONE)
                    view.setTextViewText(R.id.zone, badgeZone?.let { "Z${it.number}" } ?: "")
                    val iconResource = when {
                        isPower || stack.id == "ride-load" -> R.drawable.ic_power
                        stack.id == "heart-rate" || isEndurance -> R.drawable.ic_heart
                        stack.id == "speed" -> R.drawable.ic_speed
                        stack.id == "distance" -> R.drawable.ic_distance
                        else -> R.drawable.ic_cadence
                    }
                    if (cachedIcon == null || cachedIconColor != contentColor) {
                        val icon = requireNotNull(context.getDrawable(iconResource)).mutate()
                        icon.setTint(contentColor)
                        val pixels = (22 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                        cachedIcon = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also {
                            icon.setBounds(0, 0, pixels, pixels)
                            icon.draw(Canvas(it))
                        }
                        cachedIconColor = contentColor
                    }
                    view.setImageViewBitmap(R.id.icon, cachedIcon)
                    view.setContentDescription(R.id.icon, context.getString(stack.titleRes))
                    val preferredUnits = recordMonitor.preferredUnits
                    val mainUnit = stack.unit ?: stack.main.format.unit(preferredUnits)
                    view.setTextViewText(R.id.unit, mainUnit)
                    val mainText = format(mainValue, stack.main.format, preferredUnits)
                    view.setTextViewText(R.id.main, mainText)
                    MainLineLayout.apply(context, view, config.viewSize.first, config.viewSize.second,
                        config.textSize, mainText, mainUnit)
                    val elevationUnit = if (stack.left.format == Format.ELEVATION) " ${Format.ELEVATION.unit(preferredUnits)}" else ""
                    val leftText = format(rawValue(stack.left, 1), stack.left.format, preferredUnits)
                    view.setTextViewText(R.id.left, "${stack.leftLabel} $leftText$elevationUnit")
                    val right = stack.right
                    view.setViewVisibility(R.id.right, if (right == null) android.view.View.GONE else android.view.View.VISIBLE)
                    view.setTextViewText(R.id.right, right?.let {
                        val value = rawValue(it, 2)
                        val unit = if (isEndurance && value != null) " W" else elevationUnit
                        "${stack.rightLabel} ${format(value, it.format, preferredUnits)}$unit"
                    } ?: "")
                    if (stack.id == "cadence") {
                        PedalFieldLayout.apply(context, view, config.viewSize.first, config.viewSize.second,
                            config.textSize, mainText, pedalLayout, showBalance, pedals, balanceWarning)
                    }
                    val record = when {
                        config.preview -> PreviewValues.record(stack.id, previewTick)
                        supportsZones -> recordMonitor.current(isPower)
                        else -> null
                    }
                    if (record != null) {
                        val recordValue = record.value.toString()
                        view.setTextViewText(R.id.main, recordValue)
                        MainLineLayout.apply(context, view, config.viewSize.first, config.viewSize.second,
                            config.textSize, recordValue, mainUnit)
                        view.setViewVisibility(R.id.zone, android.view.View.GONE)
                        view.setViewVisibility(R.id.right, android.view.View.GONE)
                        val message = when {
                            record.duration == HEART_RECORD -> context.getString(R.string.record_heart_notice)
                            record.duration < 60 -> context.getString(R.string.record_seconds_notice, record.duration)
                            else -> context.getString(R.string.record_minutes_notice, record.duration / 60)
                        }
                        view.setTextViewText(R.id.left, message)
                    }
                    emitter.updateView(view)
                    if (config.preview) previewTick++
                    delay(1100) // Karoo permits at most one RemoteViews update per second.
                }
            } finally {
                pedalIds.values.forEach(system::removeConsumer)
            }
        }
        emitter.setCancellable {
            scope.cancel()
            ids.forEach(system::removeConsumer)
            profileId?.let(system::removeConsumer)
        }
    }


}

internal fun format(value: Double?, format: Format, preferred: PreferredUnit?): String {
    if (value == null || !value.isFinite() || (value < 0 && format != Format.DRIFT)) return "–"
    return when(format) {
        Format.RATIO -> String.format(Locale.getDefault(), "%.2f", value)
        Format.DRIFT -> String.format(Locale.getDefault(), "%+.1f%%", value)
        Format.INTEGER -> String.format(Locale.getDefault(), "%.0f", value)
        Format.SPEED -> String.format(Locale.getDefault(), "%.1f",
            if (preferred?.distance == IMPERIAL) value / 0.44704 else value * 3.6)
        Format.DISTANCE -> String.format(Locale.getDefault(), "%.1f",
            value / if (preferred?.distance == IMPERIAL) 1609.344 else 1000.0)
        Format.ELEVATION -> String.format(Locale.getDefault(), "%.0f",
            if (preferred?.elevation == IMPERIAL) value / 0.3048 else value)
    }
}
