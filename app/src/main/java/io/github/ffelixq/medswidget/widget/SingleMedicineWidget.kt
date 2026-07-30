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
import io.github.ffelixq.medswidget.R
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DisplayTransform
import io.github.ffelixq.medswidget.ui.MainActivity
import io.github.ffelixq.medswidget.util.TimeFormatting
import kotlinx.coroutines.launch

class SingleMedicineWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(110.dp, 130.dp),
                DpSize(180.dp, 130.dp),
            ),
        )

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val graph = MedsApplication.graph(context)
        graph.prepareTemporalStateForWidgetRender()
        val snapshot = graph.snapshotStore.read()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val configuration = graph.configurationStore.get(appWidgetId)
        provideContent {
            SingleMedicineWidgetContent(snapshot, configuration, appWidgetId)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
@androidx.glance.GlanceComposable
internal fun SingleMedicineWidgetContent(
    snapshot: WidgetSnapshot,
    configuration: SingleWidgetConfiguration?,
    appWidgetId: Int?,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.background)
                .cornerRadius(18.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        when {
            snapshot.isLoading -> {
                WidgetHeader("Meds Widget", snapshot.compactStatus())
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Loading medicines…",
                    style = WidgetTextStyles.body,
                    maxLines = 2,
                )
            }

            !snapshot.signedIn -> {
                WidgetHeader("Meds Widget")
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = "Open the app to sign in",
                    modifier =
                        GlanceModifier.clickable(
                            actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)),
                        ),
                    style = WidgetTextStyles.body,
                    maxLines = 2,
                )
            }

            configuration == null || configuration.ownerUid != snapshot.ownerUid -> {
                WidgetHeader("Choose a medicine")
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Tap and reconfigure this widget.",
                    modifier =
                        GlanceModifier.clickable(
                            actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)),
                        ),
                    style = WidgetTextStyles.body,
                    maxLines = 2,
                )
            }

            snapshot.medicine(configuration.medicineId) == null -> {
                WidgetHeader("Medicine unavailable")
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "It was archived or deleted. Reconfigure this widget.",
                    modifier =
                        GlanceModifier.clickable(
                            actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)),
                        ),
                    style = WidgetTextStyles.body,
                    maxLines = 3,
                )
            }

            else -> {
                val medicine = requireNotNull(snapshot.medicine(configuration.medicineId))
                WidgetHeader(
                    title = DisplayTransform.truncate(medicine.name, 28),
                    status = snapshot.compactStatus(),
                )
                Spacer(GlanceModifier.height(2.dp))
                snapshot.rowsForMedicine(medicine.id).forEach { row ->
                    WidgetDoseRowContent(
                        row = row,
                        source = CheckSource.WIDGET_2X2,
                        appWidgetId = appWidgetId,
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
@androidx.glance.GlanceComposable
internal fun WidgetHeader(
    title: String,
    status: String? = null,
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
            style = WidgetTextStyles.title,
            maxLines = 1,
        )
        status?.let {
            Text(text = it, style = WidgetTextStyles.supporting, maxLines = 1)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
@androidx.glance.GlanceComposable
internal fun WidgetDoseRowContent(
    row: WidgetDoseRow,
    source: CheckSource,
    appWidgetId: Int? = null,
    showMedicineName: Boolean = false,
) {
    val context = LocalContext.current
    val completionTime =
        row.checkedAt?.let {
            TimeFormatting.compact(
                context = context,
                instant = it,
                timezoneId = row.checkedTimezone,
            )
        }
    val accessibilityLabel =
        context.getString(
            if (row.isTaken) {
                R.string.widget_dose_taken_description
            } else {
                R.string.widget_dose_not_taken_description
            },
            row.medicineName,
            row.label,
        )
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
    val action =
        if (row.isTaken) {
            actionStartActivity(Intent(context, MainActivity::class.java))
        } else {
            actionRunCallback<CheckDoseAction>(parameters)
        }
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = accessibilityLabel }
                .clickable(action),
        verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
    ) {
        Text(
            text = if (row.isTaken) "☑" else "☐",
            style = WidgetTextStyles.check,
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (showMedicineName) {
                Text(
                    text = DisplayTransform.truncate(row.medicineName, 22),
                    style = WidgetTextStyles.supporting,
                    maxLines = 1,
                )
            }
            Text(
                text = DisplayTransform.truncate(row.label, 30),
                style = WidgetTextStyles.body,
                maxLines = 1,
            )
        }
        completionTime?.let {
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = it,
                style = WidgetTextStyles.supporting,
                maxLines = 1,
            )
        }
    }
}

internal object WidgetTextStyles {
    val title = TextStyle(color = WidgetColors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(color = WidgetColors.foreground, fontSize = 14.sp)
    val check = TextStyle(color = WidgetColors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val supporting = TextStyle(color = WidgetColors.foreground, fontSize = 12.sp)
}

internal object WidgetColors {
    val background = ColorProvider(Color(0xFFFFFDF8), Color(0xFF1B1C1A))
    val foreground = ColorProvider(Color(0xFF1B1C1A), Color(0xFFE4E3DF))
    val accent = ColorProvider(Color(0xFF256C5A), Color(0xFF82D5BC))
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
