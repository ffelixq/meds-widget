package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.ApplePressableCard
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun ShowcaseMoreScreen(
    state: MainUiState,
    accessibilityState: AccessibilityPreferencesState,
    contentPadding: PaddingValues,
    onExperienceMode: (ExperienceMode) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgetSetup: () -> Unit,
    onCheckPreview: (DoseRow) -> Unit,
    onStartCountdownPreview: (DoseRow) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "More",
                subtitle = "Accessibility, widgets, appearance, privacy, account, and data tools.",
            )
        }
        item {
            AccessibilityControlsCard(
                state = accessibilityState,
                onExperienceMode = onExperienceMode,
                onTextSize = onTextSize,
            )
        }
        item {
            ShowcaseMoreLink(
                title = "Settings, privacy & account",
                subtitle = "Theme, daily reset, account, privacy, reminders, and export",
                onClick = onOpenSettings,
            )
        }
        item {
            AppleSectionHeader(
                title = "Widget Studio",
                supportingText =
                    "Preview every widget and manage the real home-screen widgets from this page.",
            )
        }
        if (state.rows.isEmpty()) {
            item {
                AppleCard {
                    Text("Add a medicine to preview your widgets.")
                }
            }
        } else {
            item {
                WidgetPreviews(
                    state = state,
                    onCheck = onCheckPreview,
                    onStartCountdown = onStartCountdownPreview,
                )
            }
        }
        item {
            WidgetSetupLink(onClick = onOpenWidgetSetup)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WidgetSetupLink(onClick: () -> Unit) {
    ApplePressableCard(
        onClick = onClick,
        modifier = Modifier.testTag("widget_setup_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Manage home-screen widgets",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Add 2×2, 4×2, or 4×4 widgets and change which medicine each 2×2 widget tracks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseMoreLink(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ApplePressableCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
