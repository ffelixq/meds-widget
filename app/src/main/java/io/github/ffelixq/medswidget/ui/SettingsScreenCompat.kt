package io.github.ffelixq.medswidget.ui

import androidx.compose.runtime.Composable
import io.github.ffelixq.medswidget.domain.ThemePreference

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onResetTime: (Int) -> Unit,
    onTheme: (ThemePreference) -> Unit,
    onDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeletePasswordAccount: (String?) -> Unit,
    onDeleteGoogleAccount: () -> Unit,
    onExport: () -> Unit,
) {
    SettingsScreen(
        state = state,
        accessibilityState = AccessibilityPreferencesState(),
        onBack = onBack,
        onResetTime = onResetTime,
        onTheme = onTheme,
        onExperienceMode = {},
        onTextSize = {},
        onDisplayName = onDisplayName,
        onSignOut = onSignOut,
        onDeletePasswordAccount = onDeletePasswordAccount,
        onDeleteGoogleAccount = onDeleteGoogleAccount,
        onExport = onExport,
    )
}
