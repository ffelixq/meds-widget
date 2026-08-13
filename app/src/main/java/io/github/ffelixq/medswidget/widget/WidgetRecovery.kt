package io.github.ffelixq.medswidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.ffelixq.medswidget.R

internal fun showAutomaticWidgetRepairFallback(
    context: Context,
    appWidgetId: Int,
    title: String,
) {
    val repairIntent =
        Intent(context, AutomaticWidgetConfigurationActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    val pendingIntent =
        PendingIntent.getActivity(
            context,
            appWidgetId,
            repairIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    val views = RemoteViews(context.packageName, R.layout.widget_repair_error)
    views.setTextViewText(R.id.widget_repair_title, title)
    views.setOnClickPendingIntent(R.id.widget_repair_button, pendingIntent)
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
}
