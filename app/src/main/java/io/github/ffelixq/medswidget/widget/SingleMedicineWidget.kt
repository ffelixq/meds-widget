package io.github.ffelixq.medswidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownDisplay
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DisplayTransform
import io.github.ffelixq.medswidget.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.Instant

class SingleMedicineWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val graph = MedsApplication.graph(context)
        graph.prepareTemporalStateForWidgetRender()
        val snapshot = graph.snapshotStore.read()
        val skippedDoseKeys = graph.skippedWidgetDoseKeys(snapshot)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val configuration = graph.configurationStore.get(appWidgetId)
        provideContent {
            SingleMedicineWidgetContent(
                snapshot = snapshot,
                configuration = configuration,
                appWidgetId = appWidgetId,
                availableSize = LocalSize.current,
                skippedDoseKeys = skippedDoseKeys,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
@androidx.glance.GlanceComposable
internal fun SingleMedicineWidgetContent(
    snapshot: WidgetSnapshot,
    configuration: SingleWidgetConfiguration?,
    appWidgetId: Int?,
    availableSize: DpSize = DpSize(180.dp, 130.dp),
    skippedDoseKeys: Set<String> = emptySet(),
) {
    val spec = WidgetLayoutSpec.forSize(availableSize, WidgetKind.SINGLE)
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.background)
                .cornerRadius(22.dp)
                .padding(spec.outerPaddingDp.dp),
    ) {
        when {
            snapshot.isLoading -> {
                WidgetHeader("Meds Widget", snapshot.compactStatus(), spec)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Loading medicines…",
                    style = WidgetTextStyles.body(spec),
                    maxLines = 2,
                )
            }

            !snapshot.signedIn -> {
                WidgetHeader("Meds Widget", spec = spec)
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = "Open the app to sign in",
                    modifier =
                        GlanceModifier.clickable(
                            actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)),
                        ),
                    style = WidgetTextStyles.body(spec),
                    maxLines = 2,
                )
            }

            configuration == null || configuration.ownerUid != snapshot.ownerUid -> {
                WidgetHeader("Choose a medicine", spec = spec)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Tap and reconfigure this widget.",
                    modifier =
                        GlanceModifier.clickable(
                            actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)),
                        ),
                    style = WidgetTextStyles.body(spec),
                    maxLines = 2,
                )
            }

            snapshot.medicine(configuration.medicineId) == null -> {
                WidgetHeader("Medicine unavailable", spec = spec)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "It was archived or deleted. Reconfigure this widget.",
                    modifier =
                        GlanceModifier.clickable(
                            actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)),
                        ),
                    style = WidgetTextStyles.body(spec),
                    maxLines = 3,
                )
            }

            else -> {
                val medicine = requireNotNull(snapshot.medicine(configuration.medicineId))
                WidgetHeader(
                    title = DisplayTransform.truncate(medicine.displayName, 28),
                    status = snapshot.compactStatus(),
                    spec = spec,
                )
                Spacer(GlanceModifier.height(2.dp))
                val rows = snapshot.rowsForMedicine(medicine.id)
                val availableRowHeight =
                    (
                        (
                            availableSize.height.value -
                                (spec.outerPaddingDp * 2 + spec.titleSp + 6)
                        ) / rows.size.coerceAtLeast(1)
                    ).toInt()
                        .coerceIn(spec.rowHeightDp, 80)
                rows.forEach { row ->
                    WidgetDoseRowContent(
                        row = row,
                        source = CheckSource.WIDGET_2X2,
                        appWidgetId = appWidgetId,
                        isSkipped = widgetDoseKey(row.medicineId, row.slot) in skippedDoseKeys,
                        spec = spec,
                        rowHeightDp = availableRowHeight,
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
@androidx.glance.GlanceComposable
internal fun WidgetHeader(
    title: String,
    status: String? = null,
    spec: WidgetLayoutSpec = WidgetLayoutSpec.forSize(DpSize(180.dp, 130.dp), WidgetKind.SINGLE),
) {
    val context = LocalContext.current
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Text(
            text = title,
            modifier = GlanceModifier.defaultWeight(),
            style = WidgetTextStyles.title(spec),
            maxLines = 1,
        )
        status?.let {
            Text(text = it, style = WidgetTextStyles.supporting(spec), maxLines = 1)
        }
    }
}

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
@androidx.glance.GlanceComposable
internal fun WidgetDoseRowContent(
    row: WidgetDoseRow,
    source: CheckSource,
    appWidgetId: Int? = null,
    showMedicineName: Boolean = false,
    isSkipped: Boolean = false,
    spec: WidgetLayoutSpec = WidgetLayoutSpec.forSize(DpSize(180.dp, 130.dp), WidgetKind.SINGLE),
    rowHeightDp: Int = spec.rowHeightDp,
    now: Instant = Instant.now(),
) {
    val context = LocalContext.current
    val parameters =
        if (appWidgetId == null) {
            actionParametersOf(
                WidgetActionParameters.MEDICINE_ID to row.medicineId,
                WidgetActionParameters.SLOT to row.slot.wireValue,
                WidgetActionParameters.SOURCE to source.wireValue,
            )
        } else {
            actionParametersOf(
                WidgetActionParameters.MEDICINE_ID to row.medicineId,
                WidgetActionParameters.SLOT to row.slot.wireValue,
                WidgetActionParameters.SOURCE to source.wireValue,
                WidgetActionParameters.APP_WIDGET_ID to appWidgetId,
            )
        }
    val completed = row.isTaken || isSkipped
    val action =
        if (completed) {
            actionStartActivity(Intent(context, MainActivity::class.java))
        } else {
            actionRunCallback<CheckDoseAction>(parameters)
        }
    val countdown = CountdownLogic.display(row.countdownMinutes, row.countdown, now)
    val countdownAction =
        if (isSkipped) {
            null
        } else {
            when (countdown.status) {
                CountdownDisplayStatus.NOT_STARTED -> {
                    actionRunCallback<StartCountdownAction>(parameters)
                }

                CountdownDisplayStatus.RUNNING,
                CountdownDisplayStatus.READY,
                -> {
                    actionStartActivity(Intent(context, MainActivity::class.java))
                }

                else -> {
                    null
                }
            }
        }
    val accessibilityLabel = widgetAccessibilityLabel(row, countdown, isSkipped)
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(rowHeightDp.dp),
        verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .height(rowHeightDp.dp)
                    .semantics { contentDescription = accessibilityLabel }
                    .clickable(action),
            verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
        ) {
            Text(
                text =
                    when {
                        row.isTaken -> "✓"
                        isSkipped -> "–"
                        else -> "○"
                    },
                style = WidgetTextStyles.check(spec),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (showMedicineName) {
                    Text(
                        text =
                            DisplayTransform.truncate(
                                row.medicineName,
                                if (spec.category == WidgetLayoutCategory.COMPACT) 18 else 26,
                            ),
                        style = WidgetTextStyles.supporting(spec),
                        maxLines = 1,
                    )
                }
                Text(
                    text =
                        DisplayTransform.truncate(
                            row.label,
                            if (spec.category == WidgetLayoutCategory.COMPACT) 24 else 34,
                        ),
                    style = WidgetTextStyles.body(spec),
                    maxLines = 1,
                )
                Text(
                    text = widgetStatusText(row, countdown, isSkipped),
                    modifier = widgetStatusModifier(row, countdown, countdownAction, isSkipped),
                    style =
                        if (row.isTaken || countdown.status == CountdownDisplayStatus.READY) {
                            WidgetTextStyles.countdownReady(spec)
                        } else {
                            WidgetTextStyles.supporting(spec)
                        },
                    maxLines = 1,
                )
            }
        }
        if (
            !completed &&
            countdownAction != null &&
            countdown.status == CountdownDisplayStatus.NOT_STARTED
        ) {
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = "START TIMER",
                modifier =
                    GlanceModifier
                        .height(rowHeightDp.dp)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Start ${row.label} wait timer" }
                        .clickable(countdownAction),
                style = WidgetTextStyles.supporting(spec),
                maxLines = 1,
            )
        }
    }
}

private fun widgetStatusModifier(
    row: WidgetDoseRow,
    countdown: CountdownDisplay,
    countdownAction: androidx.glance.action.Action?,
    isSkipped: Boolean,
): GlanceModifier {
    val opensTimerDetails =
        countdown.status == CountdownDisplayStatus.RUNNING ||
            countdown.status == CountdownDisplayStatus.READY
    return if (!row.isTaken && !isSkipped && countdownAction != null && opensTimerDetails) {
        GlanceModifier
            .semantics { contentDescription = "Open ${row.label} wait timer details" }
            .clickable(countdownAction)
    } else {
        GlanceModifier
    }
}

private fun widgetStatusText(
    row: WidgetDoseRow,
    countdown: CountdownDisplay,
    isSkipped: Boolean,
): String =
    when {
        row.isTaken -> {
            "TAKEN"
        }

        isSkipped -> {
            "NOT TAKEN"
        }

        countdown.status == CountdownDisplayStatus.READY -> {
            "TAKE NOW"
        }

        countdown.status == CountdownDisplayStatus.RUNNING -> {
            "WAIT ${countdown.text.orEmpty()}"
        }

        else -> {
            "NOT RECORDED"
        }
    }

private fun widgetAccessibilityLabel(
    row: WidgetDoseRow,
    countdown: CountdownDisplay,
    isSkipped: Boolean,
): String {
    val status =
        when {
            row.isTaken -> {
                "taken; open the app for details"
            }

            isSkipped -> {
                "not taken; open the app to change this record"
            }

            countdown.status == CountdownDisplayStatus.READY -> {
                "you can take it now; tap to mark as taken"
            }

            countdown.status == CountdownDisplayStatus.RUNNING -> {
                "wait ${countdown.text.orEmpty()}; tap to mark as taken if you already took it"
            }

            else -> {
                "not recorded as taken; tap to mark as taken"
            }
        }
    return "${row.medicineName}, ${row.label}, $status"
}

internal object WidgetTextStyles {
    fun title(spec: WidgetLayoutSpec) =
        TextStyle(
            color = WidgetColors.foreground,
            fontSize = spec.titleSp.sp,
            fontWeight = FontWeight.Bold,
        )

    fun body(spec: WidgetLayoutSpec) =
        TextStyle(
            color = WidgetColors.foreground,
            fontSize = spec.bodySp.sp,
        )

    fun check(spec: WidgetLayoutSpec) =
        TextStyle(
            color = WidgetColors.accent,
            fontSize = spec.checkSp.sp,
            fontWeight = FontWeight.Bold,
        )

    fun supporting(spec: WidgetLayoutSpec) =
        TextStyle(
            color = WidgetColors.foreground,
            fontSize = spec.supportingSp.sp,
        )

    fun countdownReady(spec: WidgetLayoutSpec) =
        TextStyle(
            color = WidgetColors.success,
            fontSize = spec.supportingSp.sp,
            fontWeight = FontWeight.Bold,
        )
}

internal object WidgetColors {
    val background = ColorProvider(Color(0xFFF2F2F7), Color(0xFF1C1C1E))
    val foreground = ColorProvider(Color(0xFF111114), Color(0xFFF5F5F7))
    val accent = ColorProvider(Color(0xFF007AFF), Color(0xFF0A84FF))
    val success = ColorProvider(Color(0xFF34C759), Color(0xFF30D158))
}

class SingleMedicineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleMedicineWidget()

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        val graph = MedsApplication.graph(context)
        graph.applicationScope.launch {
            appWidgetIds.forEach { graph.configurationStore.remove(it) }
        }
    }
}
