package io.github.ffelixq.medswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.security.SensitiveWindowProtection
import io.github.ffelixq.medswidget.ui.MainActivity
import io.github.ffelixq.medswidget.ui.theme.MedsWidgetTheme
import kotlinx.coroutines.launch

enum class AutomaticWidgetKind {
    ALL_MEDICINES,
    DASHBOARD,
}

internal fun automaticWidgetKind(
    packageName: String,
    provider: ComponentName?,
): AutomaticWidgetKind? =
    when (provider) {
        ComponentName(packageName, AllMedicinesWidgetReceiver::class.java.name) -> {
            AutomaticWidgetKind.ALL_MEDICINES
        }

        ComponentName(packageName, DashboardWidgetReceiver::class.java.name) -> {
            AutomaticWidgetKind.DASHBOARD
        }

        else -> {
            null
        }
    }

class AutomaticWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SensitiveWindowProtection.apply(this)
        appWidgetId =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultIntent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val kind = automaticWidgetKind(packageName, manager.getAppWidgetInfo(appWidgetId)?.provider)
        if (kind == null) {
            finish()
            return
        }
        val graph = MedsApplication.graph(this)
        val widget = kind.widget()
        setContent {
            MedsWidgetTheme {
                val snapshot by graph.snapshotStore.flow.collectAsStateWithLifecycle(
                    initialValue = WidgetSnapshot(isLoading = true),
                )
                AutomaticWidgetConfigurationScreen(
                    title = kind.title(),
                    snapshot = snapshot,
                    onRepair = {
                        lifecycleScope.launch {
                            graph.prepareTemporalStateForWidgetRender()
                            val glanceId =
                                runCatching {
                                    GlanceAppWidgetManager(this@AutomaticWidgetConfigurationActivity)
                                        .getGlanceIdBy(appWidgetId)
                                }.getOrNull()
                            if (glanceId != null) {
                                widget.update(this@AutomaticWidgetConfigurationActivity, glanceId)
                                setResult(RESULT_OK, resultIntent)
                                finish()
                            }
                        }
                    },
                    onOpenApp = {
                        startActivity(Intent(this@AutomaticWidgetConfigurationActivity, MainActivity::class.java))
                    },
                    onCancel = ::finish,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun AutomaticWidgetConfigurationScreen(
    title: String,
    snapshot: WidgetSnapshot,
    onRepair: () -> Unit,
    onOpenApp: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "This widget follows today’s active medicines automatically, so there is no medicine " +
                "selector. Use Repair widget to refresh its saved home-screen content.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            snapshot.isLoading -> {
                Text("Refreshing medicines…")
            }

            !snapshot.signedIn -> {
                Text("Sign in before repairing this widget.")
                Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Meds Widget")
                }
            }

            else -> {
                Text(
                    "Your account is ready. Repairing keeps the same widget and refreshes its content.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRepair, modifier = Modifier.fillMaxWidth()) {
                    Text("Repair widget")
                }
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

private fun AutomaticWidgetKind.widget(): GlanceAppWidget =
    when (this) {
        AutomaticWidgetKind.ALL_MEDICINES -> AllMedicinesWidget()
        AutomaticWidgetKind.DASHBOARD -> DashboardWidget()
    }

private fun AutomaticWidgetKind.title(): String =
    when (this) {
        AutomaticWidgetKind.ALL_MEDICINES -> "Repair 4×2 widget"
        AutomaticWidgetKind.DASHBOARD -> "Repair 4×4 widget"
    }
