package io.github.ffelixq.medswidget.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ffelixq.medswidget.MedsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetPinSuccessReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val appWidgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val graph = MedsApplication.graph(context)
                graph.prepareTemporalStateForWidgetRender()
                graph.widgetUpdater.updateAll()
            } finally {
                result.finish()
            }
        }
    }
}
