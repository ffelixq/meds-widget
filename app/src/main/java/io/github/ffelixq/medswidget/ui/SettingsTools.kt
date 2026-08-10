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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill
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
    var showExportWarning by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionGranted = granted
        }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppleCard {
            AppleSectionHeader(
                title = "Reminders",
                supportingText =
                    "Set a reminder time inside each medicine slot. Reminders are scheduled on this device.",
            )
            if (notificationPermissionGranted) {
                AppleStatusPill(
                    text = "Notifications enabled",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.secondary,
                )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AppleCard {
            AppleSectionHeader(
                title = "Home-screen widgets",
                supportingText =
                    "Pin a widget directly, or add it later from your launcher's widget picker.",
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
        }

        AppleCard {
            AppleSectionHeader(
                title = "Data export",
                supportingText =
                    "Export your medicine setup and dose history as a CSV file you can save or share.",
            )
            OutlinedButton(
                onClick = { showExportWarning = true },
                modifier = Modifier.fillMaxWidth().testTag("export_csv"),
            ) {
                Text("Export CSV")
            }
        }
    }

    if (showExportWarning) {
        ExportConfirmationDialog(
            onDismiss = { showExportWarning = false },
            onConfirm = {
                showExportWarning = false
                onExport()
            },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ExportConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export sensitive data?") },
        text = {
            Text(
                "The CSV contains medicine names, notes, dose history, skip reasons, and timestamps. " +
                    "Any app you share it with can keep a copy.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_export"),
            ) {
                Text("Continue to share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
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
