package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseSlot

private val countdownPresets = listOf(30, 60, 90, 120)

@Suppress("FunctionNaming")
@Composable
internal fun CountdownEditor(
    minutes: Int?,
    onMinutesChange: (Int?) -> Unit,
    error: String?,
    tag: String,
) {
    var customMode by rememberSaveable(tag, minutes) {
        mutableStateOf(minutes != null && minutes !in countdownPresets)
    }
    TimingOptionSurface(
        tag = "${tag}_countdown_group",
        enabled = minutes != null,
    ) {
        CountdownHeader(minutes, tag, onMinutesChange)
        if (minutes != null) {
            CountdownDurationControls(
                minutes = minutes,
                customMode = customMode,
                onCustomModeChange = { customMode = it },
                onMinutesChange = onMinutesChange,
                error = error,
                tag = tag,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun CountdownDurationControls(
    minutes: Int,
    customMode: Boolean,
    onCustomModeChange: (Boolean) -> Unit,
    onMinutesChange: (Int?) -> Unit,
    error: String?,
    tag: String,
) {
    Text(
        "Countdown duration",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        countdownPresets.forEach { preset ->
            FilterChip(
                selected = minutes == preset && !customMode,
                onClick = {
                    onCustomModeChange(false)
                    onMinutesChange(preset)
                },
                label = { Text(CountdownLogic.formatDuration(preset)) },
            )
        }
    }
    FilterChip(
        selected = customMode,
        onClick = {
            onCustomModeChange(true)
            if (minutes in countdownPresets) onMinutesChange(45)
        },
        label = { Text("Custom duration") },
    )
    if (customMode) CustomCountdownFields(minutes, tag, onMinutesChange)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Suppress("FunctionNaming")
@Composable
private fun CountdownHeader(
    minutes: Int?,
    tag: String,
    onMinutesChange: (Int?) -> Unit,
) {
    TimingToggleHeader(
        title = "After-meal countdown",
        supportingText =
            minutes?.let {
                "Duration ${CountdownLogic.formatDuration(it)} · start it after eating"
            } ?: "Start a timer after eating; it never marks the dose as taken.",
        enabled = minutes != null,
        tag = "${tag}_countdown_toggle",
        contentDescription = "After-meal countdown",
        onEnabledChange = { enabled -> onMinutesChange(if (enabled) 30 else null) },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun CustomCountdownFields(
    minutes: Int,
    tag: String,
    onMinutesChange: (Int?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = (minutes / 60).toString(),
            onValueChange = { hours ->
                val parsed = hours.filter(Char::isDigit).toIntOrNull() ?: 0
                onMinutesChange(parsed * 60 + minutes % 60)
            },
            label = { Text("Hours") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("${tag}_countdown_hours"),
        )
        OutlinedTextField(
            value = (minutes % 60).toString(),
            onValueChange = { minutePart ->
                val parsed = minutePart.filter(Char::isDigit).toIntOrNull() ?: 0
                onMinutesChange(minutes / 60 * 60 + parsed)
            },
            label = { Text("Minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("${tag}_countdown_minutes"),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun ReminderEditor(
    slot: DoseSlot,
    minutesAfterMidnight: Int?,
    onMinutesChange: (Int?) -> Unit,
    error: String?,
) {
    val tag = slot.wireValue
    val defaultMinutes = defaultReminderMinutes(slot)
    TimingOptionSurface(
        tag = "${tag}_reminder_group",
        enabled = minutesAfterMidnight != null,
    ) {
        ReminderHeader(
            minutesAfterMidnight = minutesAfterMidnight,
            defaultMinutes = defaultMinutes,
            tag = tag,
            onMinutesChange = onMinutesChange,
        )
        if (minutesAfterMidnight != null) {
            ReminderTimeFields(minutesAfterMidnight, tag, onMinutesChange)
            Text(
                "This reminder stays on this device; the medicine schedule still syncs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ReminderHeader(
    minutesAfterMidnight: Int?,
    defaultMinutes: Int,
    tag: String,
    onMinutesChange: (Int?) -> Unit,
) {
    val formattedTime = minutesAfterMidnight?.let(::formatClockMinutes)
    TimingToggleHeader(
        title = "Time reminder",
        supportingText =
            formattedTime?.let { "Device reminder at $it" }
                ?: "Get a device-local reminder at a specific clock time.",
        enabled = minutesAfterMidnight != null,
        tag = "${tag}_reminder_toggle",
        contentDescription = "Time reminder",
        onEnabledChange = { onMinutesChange(if (it) defaultMinutes else null) },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun ReminderTimeFields(
    minutesAfterMidnight: Int,
    tag: String,
    onMinutesChange: (Int?) -> Unit,
) {
    Text(
        "Reminder time",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = (minutesAfterMidnight / 60).toString().padStart(2, '0'),
            onValueChange = { hourText ->
                val hour = hourText.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 23) ?: 0
                onMinutesChange(hour * 60 + minutesAfterMidnight % 60)
            },
            label = { Text("Hour") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("${tag}_reminder_hour"),
        )
        OutlinedTextField(
            value = (minutesAfterMidnight % 60).toString().padStart(2, '0'),
            onValueChange = { minuteText ->
                val minute = minuteText.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 59) ?: 0
                onMinutesChange(minutesAfterMidnight / 60 * 60 + minute)
            },
            label = { Text("Minute") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("${tag}_reminder_minute"),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TimingOptionSurface(
    tag: String,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color =
            if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun TimingToggleHeader(
    title: String,
    supportingText: String,
    enabled: Boolean,
    tag: String,
    contentDescription: String,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                ).semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    role = Role.Switch
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

private fun formatClockMinutes(minutesAfterMidnight: Int): String =
    "%02d:%02d".format(
        minutesAfterMidnight / 60,
        minutesAfterMidnight % 60,
    )
