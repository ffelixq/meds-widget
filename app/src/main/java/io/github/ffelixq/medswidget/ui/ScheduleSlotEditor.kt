package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.DoseSlot

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun SlotEditor(
    slot: DoseSlot,
    state: SlotEditorState,
    onStateChange: (SlotEditorState) -> Unit,
    errors: Map<String, String>,
) {
    val tag = slot.wireValue
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("${tag}_schedule_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SlotHeader(slot, state) { onStateChange(state.copy(enabled = it)) }
            if (state.enabled) {
                SlotLabelEditor(tag, state, onStateChange, errors)
                OptionalTimingIntro()
                CountdownEditor(
                    minutes = state.countdownMinutes,
                    onMinutesChange = { onStateChange(state.copy(countdownMinutes = it)) },
                    error = errors["${tag}CountdownMinutes"],
                    tag = tag,
                )
                ReminderEditor(
                    slot = slot,
                    minutesAfterMidnight = state.reminderMinutes,
                    onMinutesChange = { onStateChange(state.copy(reminderMinutes = it)) },
                    error = errors["${tag}ReminderMinutes"],
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SlotHeader(
    slot: DoseSlot,
    state: SlotEditorState,
    onEnabledChange: (Boolean) -> Unit,
) {
    val tag = slot.wireValue
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("${tag}_toggle")
                .toggleable(
                    value = state.enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                ).semantics(mergeDescendants = true) {
                    contentDescription = "${slot.defaultLabel} slot"
                    role = Role.Switch
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(slot.defaultLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.enabled) "Included in the daily routine" else "Not scheduled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.enabled, onCheckedChange = null)
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SlotLabelEditor(
    tag: String,
    state: SlotEditorState,
    onStateChange: (SlotEditorState) -> Unit,
    errors: Map<String, String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Dose label",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.label,
            onValueChange = { onStateChange(state.copy(label = it.take(61))) },
            label = { Text("Label shown for this dose") },
            singleLine = true,
            isError = errors["${tag}Label"] != null,
            supportingText = { Text(errors["${tag}Label"] ?: "${state.label.length}/60") },
            modifier = Modifier.fillMaxWidth().testTag("${tag}_label"),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun OptionalTimingIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Optional timing", style = MaterialTheme.typography.titleSmall)
        Text(
            "Use these only when this dose depends on a meal or a specific clock time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
