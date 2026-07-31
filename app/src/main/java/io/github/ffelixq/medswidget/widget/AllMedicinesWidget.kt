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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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

class AllMedicinesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val graph = MedsApplication.graph(context)
        graph.prepareTemporalStateForWidgetRender()
        val snapshot = graph.snapshotStore.read()
        provideContent {
            AllMedicinesWidgetContent(snapshot, LocalSize.current)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
@androidx.glance.GlanceComposable
internal fun AllMedicinesWidgetContent(
    snapshot: WidgetSnapshot,
    availableSize: DpSize = DpSize(320.dp, 150.dp),
) {
    val context = LocalContext.current
    val spec = WidgetLayoutSpec.forSize(availableSize, WidgetKind.ALL)
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.background)
                .cornerRadius(18.dp)
                .padding(spec.outerPaddingDp.dp),
    ) {
        val progress = CompletionProgress(snapshot.rows.count(WidgetDoseRow::isTaken), snapshot.rows.size)
        val status =
            if (snapshot.isLoading) {
                snapshot.compactStatus().orEmpty()
            } else {
                listOfNotNull(progress.compactDisplay, snapshot.compactStatus()).joinToString(" · ")
            }
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
                text = status,
                style = WidgetTextStyles.supporting(spec),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        when {
            snapshot.isLoading -> {
                Text(
                    text = "Loading medicines…",
                    style = WidgetTextStyles.body(spec),
                    maxLines = 2,
                )
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
                    text = "No active medicines. Add one in the app.",
                    modifier =
                        GlanceModifier.clickable(
                            actionStartActivity(Intent(context, MainActivity::class.java)),
                        ),
                    style = WidgetTextStyles.body(spec),
                    maxLines = 2,
                )
            }

            else -> {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(
                        snapshot.rows,
                        itemId = { row ->
                            "${row.medicineId}_${row.slot.wireValue}".hashCode().toLong()
                        },
                    ) {
                        WidgetDoseRowContent(
                            row = it,
                            source = CheckSource.WIDGET_4X2,
                            showMedicineName = true,
                            spec = spec,
                        )
                    }
                }
            }
        }
    }
}

class AllMedicinesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllMedicinesWidget()
}
