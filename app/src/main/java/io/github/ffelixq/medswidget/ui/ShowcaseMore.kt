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
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
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
                subtitle = "Widgets, appearance, privacy, account, and data tools.",
            )
        }
        item {
            ShowcaseMoreLink(
                title = "Settings & appearance",
                subtitle = "Theme, daily reset time, account, reminders, and export",
                onClick = onOpenSettings,
            )
        }
        item {
            ShowcaseMoreLink(
                title = "Privacy & security",
                subtitle = "Review lock-screen, export, widget, network, and account protections",
                onClick = onOpenSettings,
            )
        }
        item {
            AppleSectionHeader(
                title = "Widget Studio",
                supportingText =
                    "The widget preview moved here so Today can stay focused on taking medicine.",
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
            AppleCard {
                AppleSectionHeader(
                    title = "Meds Widget",
                    supportingText = "Clear actions, calm hierarchy, and explicit privacy.",
                )
                Text(
                    "Your home-screen widgets still use the same tested Glance callbacks, " +
                        "cloud sync, countdowns, and audit history as before.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
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
                        .size(42.dp)
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
