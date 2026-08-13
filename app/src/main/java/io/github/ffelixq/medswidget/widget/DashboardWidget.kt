package io.github.ffelixq.medswidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CompletionProgress
import io.github.ffelixq.medswidget.ui.MainActivity

class DashboardWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val graph = MedsApplication.graph(context)
        graph.prepareTemporalStateForWidgetRender()
        val snapshot = graph.snapshotStore.read()
        val skippedDoseKeys = graph.skippedWidgetDoseKeys(snapshot)
        provideContent {
            DashboardWidgetContent(
                snapshot = snapshot,
                skippedDoseKeys = skippedDoseKeys,
                availableSize = LocalSize.current,
            )
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        showAutomaticWidgetRepairFallback(context, appWidgetId, "Meds Widget · Today dashboard")
    }
}

@Suppress("FunctionNaming")
@Composable
@androidx.glance.GlanceComposable
internal fun DashboardWidgetContent(
    snapshot: WidgetSnapshot,
    skippedDoseKeys: Set<String> = emptySet(),
    availableSize: DpSize = DpSize(320.dp, 300.dp),
) {
    val spec = WidgetLayoutSpec.forSize(availableSize, WidgetKind.ALL)
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.background)
                .cornerRadius(22.dp)
                .padding(spec.outerPaddingDp.dp),
    ) {
        DashboardWidgetHeader(snapshot = snapshot, spec = spec)
        Spacer(GlanceModifier.height(4.dp))
        DashboardWidgetBody(
            snapshot = snapshot,
            skippedDoseKeys = skippedDoseKeys,
            availableSize = availableSize,
            spec = spec,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
@androidx.glance.GlanceComposable
private fun DashboardWidgetHeader(
    snapshot: WidgetSnapshot,
    spec: WidgetLayoutSpec,
) {
    val context = LocalContext.current
    val progress = CompletionProgress(snapshot.rows.count(WidgetDoseRow::isTaken), snapshot.rows.size)
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Text(
            text = "Today’s medicines",
            style = WidgetTextStyles.title(spec),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        Text(
            text = progress.compactDisplay,
            style = WidgetTextStyles.supporting(spec),
            maxLines = 1,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
@androidx.glance.GlanceComposable
private fun DashboardWidgetBody(
    snapshot: WidgetSnapshot,
    skippedDoseKeys: Set<String>,
    availableSize: DpSize,
    spec: WidgetLayoutSpec,
) {
    val context = LocalContext.current
    when {
        snapshot.isLoading -> {
            Text("Loading medicines…", style = WidgetTextStyles.body(spec), maxLines = 2)
        }

        !snapshot.signedIn -> {
            Text(
                text = "Open the app to sign in",
                modifier =
                    GlanceModifier.clickable(
                        actionStartActivity(Intent(context, MainActivity::class.java)),
                    ),
                style = WidgetTextStyles.body(spec),
                maxLines = 2,
            )
        }

        snapshot.rows.isEmpty() -> {
            Text(
                text = "No medicines due today",
                modifier =
                    GlanceModifier.clickable(
                        actionStartActivity(Intent(context, MainActivity::class.java)),
                    ),
                style = WidgetTextStyles.body(spec),
                maxLines = 2,
            )
        }

        else -> {
            val rows = automaticWidgetRows(snapshot.rows, availableSize, spec)
            rows.visible.forEach { row ->
                WidgetDoseRowContent(
                    row = row,
                    source = CheckSource.WIDGET_4X4,
                    showMedicineName = true,
                    isSkipped = widgetDoseKey(row.medicineId, row.slot) in skippedDoseKeys,
                    spec = spec,
                    rowHeightDp = rows.rowHeightDp,
                )
            }
            if (rows.hiddenCount > 0) {
                Text(
                    text = "+${rows.hiddenCount} more · Open app",
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    style = WidgetTextStyles.supporting(spec),
                    maxLines = 1,
                )
            }
        }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}
