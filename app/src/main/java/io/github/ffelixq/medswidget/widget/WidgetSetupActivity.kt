package io.github.ffelixq.medswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.security.SensitiveWindowProtection
import io.github.ffelixq.medswidget.ui.theme.MedsWidgetTheme

class WidgetSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SensitiveWindowProtection.apply(this)
        val graph = MedsApplication.graph(this)
        setContent {
            MedsWidgetTheme {
                val snapshot by graph.snapshotStore.flow.collectAsStateWithLifecycle(
                    initialValue = WidgetSnapshot(isLoading = true),
                )
                WidgetSetupScreen(
                    graph = graph,
                    snapshot = snapshot,
                    onBack = ::finish,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
internal fun WidgetSetupScreen(
    graph: AppGraph,
    snapshot: WidgetSnapshot,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var configurations by remember { mutableStateOf<Map<Int, SingleWidgetConfiguration>>(emptyMap()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val singleWidgetIds = remember(refreshKey) { widgetIds(context, SingleMedicineWidgetReceiver::class.java) }
    val allWidgetIds = remember(refreshKey) { widgetIds(context, AllMedicinesWidgetReceiver::class.java) }
    val dashboardWidgetIds = remember(refreshKey) { widgetIds(context, DashboardWidgetReceiver::class.java) }

    LaunchedEffect(refreshKey, singleWidgetIds.contentHashCode()) {
        configurations =
            buildMap {
                singleWidgetIds.forEach { id ->
                    graph.configurationStore.get(id)?.let { put(id, it) }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Configure what Meds Widget shows here. Samsung still controls the final home-screen " +
                        "placement and resizing step.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SingleMedicineSetupCard(
                    snapshot = snapshot,
                    widgetIds = singleWidgetIds,
                    configurations = configurations,
                    onConfigure = { widgetId ->
                        context.startActivity(
                            Intent(context, SingleWidgetConfigurationActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        )
                    },
                    onAdd = {
                        requestPinWidget(context, SingleMedicineWidgetReceiver::class.java)
                    },
                )
            }

            item {
                AutomaticWidgetSetupCard(
                    title = "4×2 · All medicines",
                    count = allWidgetIds.size,
                    description =
                        "Automatically shows your active medicine rows for today. " +
                            "There is no medicine selector for this widget.",
                    buttonText = "Add 4×2 widget",
                    onAdd = {
                        requestPinWidget(context, AllMedicinesWidgetReceiver::class.java)
                    },
                )
            }

            item {
                AutomaticWidgetSetupCard(
                    title = "4×4 · Today dashboard",
                    count = dashboardWidgetIds.size,
                    description =
                        "Automatically shows the larger daily dashboard. " +
                            "There is no medicine selector for this widget.",
                    buttonText = "Add 4×4 widget",
                    onAdd = {
                        requestPinWidget(context, DashboardWidgetReceiver::class.java)
                    },
                )
            }

            item {
                Text(
                    "To remove a widget, remove it from the home screen normally. " +
                        "You can return here at any time to change a 2×2 widget's medicine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SingleMedicineSetupCard(
    snapshot: WidgetSnapshot,
    widgetIds: IntArray,
    configurations: Map<Int, SingleWidgetConfiguration>,
    onConfigure: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WidgetSetupHeader(
                title = "2×2 · One medicine",
                count = widgetIds.size,
                description = "Each 2×2 widget tracks one medicine. Configure each copy separately.",
            )

            when {
                snapshot.isLoading -> Text("Loading medicines…")
                !snapshot.signedIn -> Text("Sign in before configuring a medicine widget.")
                snapshot.medicines.isEmpty() -> Text("Add a medicine before configuring a 2×2 widget.")
                widgetIds.isEmpty() -> {
                    Text(
                        "No 2×2 widgets are currently detected on the home screen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        widgetIds.sorted().forEachIndexed { index, widgetId ->
                            val configuration = configurations[widgetId]
                            val medicine = configuration?.let { snapshot.medicine(it.medicineId) }
                            ExistingSingleWidgetRow(
                                label = "2×2 widget ${index + 1}",
                                medicineName = medicine?.displayName ?: "Not configured",
                                configured = medicine != null,
                                onConfigure = { onConfigure(widgetId) },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                enabled = snapshot.signedIn && snapshot.medicines.isNotEmpty(),
            ) {
                Text("Add 2×2 widget")
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ExistingSingleWidgetRow(
    label: String,
    medicineName: String,
    configured: Boolean,
    onConfigure: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                medicineName,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (configured) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
        OutlinedButton(onClick = onConfigure) {
            Text(if (configured) "Change" else "Configure")
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AutomaticWidgetSetupCard(
    title: String,
    count: Int,
    description: String,
    buttonText: String,
    onAdd: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WidgetSetupHeader(title = title, count = count, description = description)
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WidgetSetupHeader(
    title: String,
    count: Int,
    description: String,
) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(
        if (count == 1) "1 on home screen" else "$count on home screen",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun widgetIds(
    context: Context,
    receiver: Class<*>,
): IntArray =
    AppWidgetManager
        .getInstance(context)
        .getAppWidgetIds(ComponentName(context, receiver))

private fun requestPinWidget(
    context: Context,
    receiver: Class<*>,
) {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(
            context,
            "Your launcher does not support adding widgets from inside the app.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }
    if (!manager.requestPinAppWidget(ComponentName(context, receiver), null, null)) {
        Toast.makeText(
            context,
            "Open your home-screen widget picker to add this widget.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
