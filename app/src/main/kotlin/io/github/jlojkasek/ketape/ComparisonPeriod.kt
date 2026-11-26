package io.github.jlojkasek.ketape

internal enum class ComparisonPeriod(val id: String, val title: Int, val shortTitle: Int, val badge: Int) {
    SEASON("s0", R.string.records_season, R.string.records_season_short, R.string.power_best_short_season),
    RECENT(RECENT_POWER_PERIOD, R.string.records_42_days, R.string.comparison_42_days, R.string.power_best_short_recent),
    ALL_TIME(ALL_TIME_POWER_PERIOD, R.string.records_all_time, R.string.records_all_time, R.string.power_best_short_all_time);

    companion object {
        const val MAX_SELECTION = 2

        fun selected(stored: String?): List<ComparisonPeriod> {
            if (stored == "both") return listOf(SEASON, RECENT)
            val ids = stored.orEmpty().split(",").toSet()
            return entries.filter { it.id in ids }.take(MAX_SELECTION).ifEmpty { listOf(SEASON) }
        }
    }
}
