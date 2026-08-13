package io.github.ffelixq.medswidget.ui

import android.content.Context
import android.content.Intent
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
import io.github.ffelixq.medswidget.widget.WidgetSetupActivity

private const val NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"

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
        PrivacySecurityCard()
        ReminderPrivacyCard(
            permissionGranted = notificationPermissionGranted,
            onRequestPermission = {
                permissionLauncher.launch(NOTIFICATION_PERMISSION)
            },
        )
        WidgetToolsCard(context)
        ExportToolsCard(onExport = { showExportWarning = true })
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
private fun PrivacySecurityCard() {
    AppleCard {
        AppleSectionHeader(
            title = "Privacy & security",
            supportingText =
                "Health data is account-scoped. Android backups and cleartext traffic are blocked, " +
                    "and supported devices hide app content from recents previews and third-party overlays.",
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
}

@Suppress("FunctionNaming")
@Composable
private fun ReminderPrivacyCard(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    AppleCard {
        AppleSectionHeader(
            title = "Reminders",
            supportingText =
                "Set a reminder time inside each medicine slot. Detailed reminder content is private " +
                    "on the lock screen and reminders stay on this device.",
        )
        when {
            permissionGranted -> {
                AppleStatusPill(
                    text = "Notifications enabled",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.secondary,
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                Button(onClick = onRequestPermission) {
                    Text("Allow medicine reminders")
                }
            }

            else -> {
                Text(
                    "Notifications are disabled in Android settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WidgetToolsCard(context: Context) {
    AppleCard {
        AppleSectionHeader(
            title = "Home-screen widgets",
            supportingText =
                "Configure existing 2×2 widgets and add any widget size from one place inside Meds Widget.",
        )
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(context, WidgetSetupActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Configure widgets")
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ExportToolsCard(onExport: () -> Unit) {
    AppleCard {
        AppleSectionHeader(
            title = "Data export",
            supportingText =
                "CSV exports contain medicine names, notes, dose history, skip reasons, and timestamps. " +
                    "Only share them with people or apps you trust.",
        )
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().testTag("export_csv"),
        ) {
            Text("Review & export CSV")
        }
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
                ContextCompat.checkSelfPermission(context, NOTIFICATION_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        )
