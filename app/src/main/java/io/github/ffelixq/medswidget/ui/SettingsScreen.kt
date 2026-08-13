package io.github.ffelixq.medswidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.BuildConfig
import io.github.ffelixq.medswidget.R
import io.github.ffelixq.medswidget.domain.ThemePreference
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill

@Suppress("FunctionNaming")
@Composable
internal fun AccountDeletionProgressScreen() {
    BackHandler(enabled = true) {
        // Deletion has crossed into a destructive operation. The fresh graph
        // and activity task replace this screen when cleanup completes.
    }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("account_deletion_blocking_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppleCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                    Text("Deleting account…", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Keep this screen open while local and cloud data are cleared.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    accessibilityState: AccessibilityPreferencesState,
    onBack: () -> Unit,
    onResetTime: (Int) -> Unit,
    onTheme: (ThemePreference) -> Unit,
    onExperienceMode: (ExperienceMode) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
    onDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeletePasswordAccount: (String?) -> Unit,
    onDeleteGoogleAccount: () -> Unit,
    onExport: () -> Unit,
) {
    var hour by rememberSaveable(state.settings.resetMinutesAfterMidnight) {
        mutableStateOf((state.settings.resetMinutesAfterMidnight / 60).toString())
    }
    var minute by rememberSaveable(state.settings.resetMinutesAfterMidnight) {
        mutableStateOf((state.settings.resetMinutesAfterMidnight % 60).toString().padStart(2, '0'))
    }
    var displayName by rememberSaveable(state.settings.displayName) { mutableStateOf(state.settings.displayName) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val controlsEnabled = !state.isDeletingAccount

    BackHandler(enabled = state.isDeletingAccount) {
        // Keep this destination active until MainActivity installs the fresh post-deletion graph.
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = controlsEnabled) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppleLargeTitle(
                title = "Settings",
                subtitle = "Appearance, account, privacy, and advanced setup.",
            )

            if (state.isDeletingAccount) {
                AppleCard(modifier = Modifier.testTag("account_deletion_progress")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Column {
                            Text("Deleting account…", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Keep this screen open while local and cloud data are cleared.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            AppleCard {
                AppleSectionHeader(
                    title = "Daily reset",
                    supportingText =
                        "Doses before this local time belong to the previous logical medication day.",
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0–23)") },
                        modifier = Modifier.weight(1f).testTag("reset_hour"),
                        singleLine = true,
                        enabled = controlsEnabled,
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        modifier = Modifier.weight(1f).testTag("reset_minute"),
                        singleLine = true,
                        enabled = controlsEnabled,
                    )
                }
                Button(
                    enabled = controlsEnabled,
                    onClick = { parseResetMinutes(hour, minute)?.let(onResetTime) },
                ) { Text("Save reset time") }
                Text(
                    "Current timezone: ${state.timezoneId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppleCard {
                AppleSectionHeader(
                    title = "Theme",
                    supportingText = "Follow the phone or choose a fixed appearance.",
                )
                ThemePreference.entries.forEach { theme ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.settings.themePreference == theme,
                                    enabled = controlsEnabled,
                                    role = Role.RadioButton,
                                    onClick = { onTheme(theme) },
                                ).testTag("theme_${theme.wireValue}")
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.settings.themePreference == theme,
                            onClick = null,
                            enabled = controlsEnabled,
                        )
                        Text(
                            theme.name.lowercase().replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            AppleCard {
                AppleSectionHeader(title = "Account", supportingText = state.accountEmail)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(80) },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_display_name"),
                    singleLine = true,
                    enabled = controlsEnabled,
                )
                Button(onClick = { onDisplayName(displayName) }, enabled = controlsEnabled) {
                    Text("Save display name")
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = controlsEnabled,
                ) {
                    Text("Sign out")
                }
                TextButton(
                    onClick = {
                        password = ""
                        deleteDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = controlsEnabled,
                ) {
                    Text(
                        "Delete account",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            SettingsTools(onExport)

            AppleCard {
                AppleSectionHeader(title = "Privacy")
                Text(
                    stringResource(R.string.privacy_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "App version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    state.isSyncPending -> {
                        AppleStatusPill(
                            text = "Waiting to synchronise",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                        Text("Settings are saved on this device and waiting to synchronise.")
                    }

                    state.isCached -> {
                        AppleStatusPill(
                            text = "Cached settings",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Showing locally cached settings.")
                    }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (deleteDialog && !state.isDeletingAccount) {
        val passwordProvider = "password" in state.providers
        AlertDialog(
            onDismissRequest = {
                password = ""
                deleteDialog = false
            },
            title = { Text("Permanently delete account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This deletes the known Firestore data for this account and then the authentication account. " +
                            "This cannot be undone.",
                    )
                    if (passwordProvider) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Current password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.testTag("delete_account_password"),
                            singleLine = true,
                        )
                    } else {
                        Text("You will be asked to sign in with Google again.")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reauthenticationPassword = password
                        password = ""
                        deleteDialog = false
                        if (passwordProvider) {
                            onDeletePasswordAccount(reauthenticationPassword)
                        } else {
                            onDeleteGoogleAccount()
                        }
                    },
                ) { Text("Delete account") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        password = ""
                        deleteDialog = false
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

private fun parseResetMinutes(
    hour: String,
    minute: String,
): Int? {
    val hours = hour.toIntOrNull() ?: return null
    val minutes = minute.toIntOrNull() ?: return null
    return if (hours in 0..23 && minutes in 0..59) hours * 60 + minutes else null
}
