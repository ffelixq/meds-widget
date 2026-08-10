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
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionGranted = granted
        }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppleCard {
            AppleSectionHeader(
                title = "Privacy & security",
                supportingText =
                    "Health data is account-scoped and protected by Firebase rules. " +
                        "This device also blocks backups, cleartext traffic, recents previews, " +
                        "and third-party overlays where Android supports it.",
            )
            AppleStatusPill(
                text = "Privacy protections active",
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "Home-screen widgets are intentionally visible while your phone is unlocked. " +
                    "Use a nickname or hidden widget name for medicines you want to keep discreet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AppleCard {
            AppleSectionHeader(
                title = "Reminders",
                supportingText =
                    "Set a reminder time inside each medicine slot. " +
                        "Lock-screen previews hide medicine details until your device allows them to be shown.",
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
                    "CSV exports contain medicine names, notes, and dose history. " +
                        "Only share them with people or apps you trust.",
            )
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Review & export CSV")
            }
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
