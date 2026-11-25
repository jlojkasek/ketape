package io.github.jlojkasek.ketape

import android.content.Context

internal fun ReserveStatus.displayLabel(context: Context): String = context.getString(when (this) {
    ReserveStatus.READY -> R.string.reserve_ready
    ReserveStatus.CONFIGURE -> R.string.reserve_configure
    ReserveStatus.PAUSED -> R.string.reserve_paused
    ReserveStatus.MISSING -> R.string.reserve_missing
    ReserveStatus.DEPLETING -> R.string.reserve_depleting
    ReserveStatus.RECOVERING -> R.string.reserve_recovering
    ReserveStatus.STABLE -> R.string.reserve_stable
    ReserveStatus.FULL -> R.string.reserve_full
})

internal fun ReserveParameters.displaySource(context: Context): String =
    if (source == "Manual CP") context.getString(R.string.reserve_manual_cp) else source
