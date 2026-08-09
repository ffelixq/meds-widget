package io.github.ffelixq.medswidget.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ffelixq.medswidget.widget.AllMedicinesWidgetReceiver
import io.github.ffelixq.medswidget.widget.DashboardWidgetReceiver
import io.github.ffelixq.medswidget.widget.SingleMedicineWidgetReceiver

@Suppress("FunctionNaming")
@Composable
internal fun SettingsTools(onExport: () -> Unit) {
    val context = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(notificationsAllowed(context))
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionGranted = granted
        }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Reminders", style = MaterialTheme.typography.titleMedium)
        Text(
            "Set a reminder time inside each medicine slot. Reminders are scheduled on this device.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (notificationPermissionGranted) {
            Text("Notifications are enabled.", style = MaterialTheme.typography.bodySmall)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ) {
                Text("Allow medicine reminders")
            }
        } else {
            Text(
                "Notifications are disabled in Android settings.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("Home-screen widgets", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pin a widget directly, or add it later from your launcher's widget picker.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { requestPin(context, SingleMedicineWidgetReceiver::class.java) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add 2×2 medicine widget")
        }
        OutlinedButton(
            onClick = { requestPin(context, AllMedicinesWidgetReceiver::class.java) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add 4×2 all-medicines widget")
        }
        OutlinedButton(
            onClick = { requestPin(context, DashboardWidgetReceiver::class.java) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add 4×4 dashboard widget")
        }

        Text("Data export", style = MaterialTheme.typography.titleMedium)
        Text(
            "Export your medicine setup and dose history as a CSV file you can save or share.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export CSV")
        }
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )

private fun requestPin(
    context: Context,
    receiver: Class<*>,
) {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return
    manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
}
