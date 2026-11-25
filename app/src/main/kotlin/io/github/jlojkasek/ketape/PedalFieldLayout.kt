package io.github.jlojkasek.ketape

import android.content.Context
import android.view.View
import android.widget.RemoteViews

/** Reuse the existing stacked field, reserving header space only for Dynamics. */
internal object PedalFieldLayout {
    fun apply(context: Context, view: RemoteViews, width: Int, height: Int, textSize: Int,
        cadence: String, layout: PedalLayout, showBalance: Boolean,
        pedals: PedalMetrics, warning: Boolean) {
        val gap = if (layout == PedalLayout.CLASSIC) 0 else (2 * context.resources.displayMetrics.density).toInt()
        view.setViewPadding(R.id.left, 0, 0, gap, 0)
        view.setViewPadding(R.id.right, gap, 0, 0, 0)
        val dynamics = layout == PedalLayout.DYNAMICS
        view.setViewVisibility(R.id.pedal_header, if (dynamics) View.VISIBLE else View.GONE)
        val balance = (if (warning) "! " else "") + pedals.balanceText
        view.setTextViewText(R.id.pedal_header, balance)
        MainLineLayout.apply(context, view, width,
            height - if (dynamics) (12 * context.resources.displayMetrics.density).toInt() else 0,
            textSize, cadence, "rpm")
        when (layout) {
            PedalLayout.CLASSIC -> if (showBalance) {
                view.setTextViewText(R.id.left, balance)
                view.setViewVisibility(R.id.right, View.GONE)
            }
            PedalLayout.PERFORMANCE -> {
                view.setTextViewText(R.id.left, balance)
                view.setTextViewText(R.id.right, pedals.combinedPsText)
                view.setViewVisibility(R.id.right, View.VISIBLE)
            }
            PedalLayout.DYNAMICS -> {
                view.setTextViewText(R.id.left, pedals.teText)
                view.setTextViewText(R.id.right, pedals.psText)
                view.setViewVisibility(R.id.right, View.VISIBLE)
            }
        }
    }
}
