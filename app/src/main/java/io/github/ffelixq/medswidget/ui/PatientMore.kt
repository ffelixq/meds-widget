package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun PatientMoreScreen(
    accessibilityState: AccessibilityPreferencesState,
    contentPadding: PaddingValues,
    onExperienceMode: (ExperienceMode) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "More",
                subtitle = "Make the app easier to read or ask a caregiver to change setup.",
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
            AppleCard {
                AppleSectionHeader(
                    title = "Need to change medicines?",
                    supportingText =
                        "Switch to Caregiver mode before changing schedules, reminders, " +
                            "widgets, or account settings.",
                )
                OutlinedButton(
                    onClick = { onExperienceMode(ExperienceMode.CAREGIVER) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                ) {
                    Text("Open caregiver tools")
                }
            }
        }
        item {
            AppleCard {
                AppleSectionHeader(title = "Help")
                Text(
                    "On Today, find the next medicine and press “I TOOK IT” after you take it. " +
                        "Use “Remind me later” if you want another reminder. " +
                        "The app does not tell you to change a dose.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                ) {
                    Text("Account and app settings")
                }
            }
        }
    }
}
