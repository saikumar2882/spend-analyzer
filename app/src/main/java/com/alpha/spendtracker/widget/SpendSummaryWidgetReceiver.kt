package com.alpha.spendtracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class SpendSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendSummaryWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SpendSummaryWidgetUpdater.scheduleMidnightRefresh(context)
    }

    // Also fires after reboot / app update / updatePeriodMillis — re-arm the midnight
    // rollover in case the chain was lost (KEEP makes this a no-op when it's pending).
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SpendSummaryWidgetUpdater.scheduleMidnightRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        SpendSummaryWidgetUpdater.cancelMidnightRefresh(context)
    }
}
