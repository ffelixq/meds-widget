package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader

@Suppress("FunctionNaming")
@Composable
internal fun AccessibilityControlsCard(
    state: AccessibilityPreferencesState,
    onExperienceMode: (ExperienceMode) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
) {
    AppleCard {
        AppleSectionHeader(
            title = "Easy to use",
            supportingText =
                "Patient mode hides setup tools and keeps daily medicine actions large and simple.",
        )
        Text("App mode", style = MaterialTheme.typography.titleMedium)
        ExperienceMode.entries.forEach { mode ->
            AccessibleChoiceRow(
                label = mode.label,
                supportingText =
                    when (mode) {
                        ExperienceMode.PATIENT -> "Simple daily medicine screen with fewer controls"
                        ExperienceMode.CAREGIVER -> "Medicine setup, widgets, detailed tools, and settings"
                    },
                selected = state.experienceMode == mode,
                onClick = { onExperienceMode(mode) },
            )
        }
        Text("Text size", style = MaterialTheme.typography.titleMedium)
        AppTextSize.entries.forEach { size ->
            AccessibleChoiceRow(
                label = size.label,
                supportingText =
                    when (size) {
                        AppTextSize.SYSTEM -> "Use your phone’s normal text size"
                        AppTextSize.LARGE -> "Make text easier to read"
                        AppTextSize.EXTRA_LARGE -> "Largest in-app text, up to Android’s 200% scale"
                    },
                selected = state.textSize == size,
                onClick = { onTextSize(size) },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AccessibleChoiceRow(
    label: String,
    supportingText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
