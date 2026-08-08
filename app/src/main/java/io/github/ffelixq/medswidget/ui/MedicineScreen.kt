package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.ValidationResult
import io.github.ffelixq.medswidget.domain.WidgetNameMode
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class SlotEditorState(
    val enabled: Boolean,
    val label: String,
    val countdownMinutes: Int?,
    val reminderMinutes: Int?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun MedicineScreen(
    medicine: Medicine?,
    onBack: () -> Unit,
    onSave: suspend (MedicineDraft) -> ValidationResult,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    activeCountdowns: List<CountdownState> = emptyList(),
) {
    var name by rememberSaveable(medicine?.id) { mutableStateOf(medicine?.name.orEmpty()) }
    var nickname by rememberSaveable(medicine?.id) { mutableStateOf(medicine?.nickname.orEmpty()) }
    var notes by rememberSaveable(medicine?.id) { mutableStateOf(medicine?.notes.orEmpty()) }
    var widgetNameMode by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.widgetNameMode ?: WidgetNameMode.FULL)
    }
    var morning by remember(medicine?.id) {
        mutableStateOf(
            SlotEditorState(
                enabled = medicine?.morningEnabled ?: false,
                label = medicine?.morningLabel ?: DoseSlot.MORNING.defaultLabel,
                countdownMinutes = medicine?.morningCountdownMinutes,
                reminderMinutes = medicine?.morningReminderMinutes,
            ),
        )
    }
    var afternoon by remember(medicine?.id) {
        mutableStateOf(
            SlotEditorState(
                enabled = medicine?.afternoonEnabled ?: true,
                label = medicine?.afternoonLabel ?: DoseSlot.AFTERNOON.defaultLabel,
                countdownMinutes = medicine?.afternoonCountdownMinutes,
                reminderMinutes = medicine?.afternoonReminderMinutes,
            ),
        )
    }
    var evening by remember(medicine?.id) {
        mutableStateOf(
            SlotEditorState(
                enabled = medicine?.eveningEnabled ?: false,
                label = medicine?.eveningLabel ?: DoseSlot.EVENING.defaultLabel,
                countdownMinutes = medicine?.eveningCountdownMinutes,
                reminderMinutes = medicine?.eveningReminderMinutes,
            ),
        )
    }
    var night by remember(medicine?.id) {
        mutableStateOf(
            SlotEditorState(
                enabled = medicine?.nightEnabled ?: true,
                label = medicine?.nightLabel ?: DoseSlot.NIGHT.defaultLabel,
                countdownMinutes = medicine?.nightCountdownMinutes,
                reminderMinutes = medicine?.nightReminderMinutes,
            ),
        )
    }
    var startDateText by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.startDate?.toString().orEmpty())
    }
    var endDateText by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.endDate?.toString().orEmpty())
    }
    var supplyEnabled by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.supplyEnabled ?: false)
    }
    var supplyInitialUnits by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.supplyInitialUnits?.toDisplayNumber().orEmpty())
    }
    var unitsPerDose by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.unitsPerDose?.toDisplayNumber() ?: "1")
    }
    var lowSupplyThreshold by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.lowSupplyThreshold?.toDisplayNumber().orEmpty())
    }
    var supplyUnitName by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.supplyUnitName ?: "tablets")
    }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isSaving by remember { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCountdownDraft by remember { mutableStateOf<MedicineDraft?>(null) }
    val scope = rememberCoroutineScope()

    fun buildDraft(): MedicineDraft? {
        val localErrors = mutableMapOf<String, String>()
        val startDate = parseDate(startDateText, "startDate", localErrors)
        val endDate = parseDate(endDateText, "endDate", localErrors)
        if (localErrors.isNotEmpty()) {
            errors = localErrors
            return null
        }
        return MedicineDraft(
            id = medicine?.id,
            name = name,
            nickname = nickname,
            notes = notes,
            widgetNameMode = widgetNameMode,
            morningEnabled = morning.enabled,
            morningLabel = morning.label,
            morningCountdownMinutes = morning.countdownMinutes,
            morningReminderMinutes = morning.reminderMinutes,
            afternoonEnabled = afternoon.enabled,
            afternoonLabel = afternoon.label,
            afternoonCountdownMinutes = afternoon.countdownMinutes,
            afternoonReminderMinutes = afternoon.reminderMinutes,
            eveningEnabled = evening.enabled,
            eveningLabel = evening.label,
            eveningCountdownMinutes = evening.countdownMinutes,
            eveningReminderMinutes = evening.reminderMinutes,
            nightEnabled = night.enabled,
            nightLabel = night.label,
            nightCountdownMinutes = night.countdownMinutes,
            nightReminderMinutes = night.reminderMinutes,
            startDate = startDate,
            endDate = endDate,
            supplyEnabled = supplyEnabled,
            supplyInitialUnits = supplyInitialUnits.toDoubleOrNull(),
            unitsPerDose = unitsPerDose.toDoubleOrNull() ?: 0.0,
            lowSupplyThreshold =
                lowSupplyThreshold
                    .takeIf(String::isNotBlank)
                    ?.toDoubleOrNull(),
            supplyUnitName = supplyUnitName,
        )
    }

    fun save(draft: MedicineDraft) {
        isSaving = true
        scope.launch {
            val result = onSave(draft)
            errors = result.errors
            isSaving = false
            if (result.isValid) onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (medicine == null) "Add medicine" else "Edit medicine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Medicine", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(101) },
                label = { Text("Medicine name") },
                singleLine = true,
                isError = "name" in errors,
                supportingText = { Text(errors["name"] ?: "${name.length}/100") },
                modifier = Modifier.fillMaxWidth().testTag("medicine_name"),
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(61) },
                label = { Text("Nickname (optional)") },
                supportingText = {
                    Text(errors["nickname"] ?: "Use this for a discreet widget name")
                },
                isError = "nickname" in errors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("medicine_nickname"),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(501) },
                label = { Text("Notes (optional)") },
                supportingText = {
                    Text(errors["notes"] ?: "Personal notes only; not medical advice")
                },
                isError = "notes" in errors,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("medicine_notes"),
            )

            Text("Widget privacy", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose what appears on your unlocked home screen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetNameMode.entries.forEach { mode ->
                    FilterChip(
                        selected = widgetNameMode == mode,
                        onClick = { widgetNameMode = mode },
                        label = {
                            Text(
                                when (mode) {
                                    WidgetNameMode.FULL -> "Full name"
                                    WidgetNameMode.NICKNAME -> "Nickname"
                                    WidgetNameMode.HIDDEN -> "Hide name"
                                },
                            )
                        },
                    )
                }
            }

            HorizontalDivider()
            Text("Daily schedule", style = MaterialTheme.typography.titleLarge)
            Text(
                "Enable any combination. Labels can describe the real routine, " +
                    "such as After breakfast or Before bed.",
                style = MaterialTheme.typography.bodySmall,
            )
            SlotEditor(DoseSlot.MORNING, morning, { morning = it }, errors)
            SlotEditor(DoseSlot.AFTERNOON, afternoon, { afternoon = it }, errors)
            SlotEditor(DoseSlot.EVENING, evening, { evening = it }, errors)
            SlotEditor(DoseSlot.NIGHT, night, { night = it }, errors)
            errors["slots"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            Text("Course", style = MaterialTheme.typography.titleLarge)
            Text(
                "Leave both dates blank for an ongoing medicine.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = startDateText,
                onValueChange = { startDateText = it.take(10) },
                label = { Text("Start date (YYYY-MM-DD)") },
                singleLine = true,
                isError = "startDate" in errors,
                supportingText = { errors["startDate"]?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("medicine_start_date"),
            )
            OutlinedTextField(
                value = endDateText,
                onValueChange = { endDateText = it.take(10) },
                label = { Text("End date (YYYY-MM-DD)") },
                singleLine = true,
                isError = "endDate" in errors || "course" in errors,
                supportingText = { Text(errors["endDate"] ?: errors["course"].orEmpty()) },
                modifier = Modifier.fillMaxWidth().testTag("medicine_end_date"),
            )

            HorizontalDivider()
            Text("Supply tracking", style = MaterialTheme.typography.titleLarge)
            ToggleRow(
                title = "Track remaining supply",
                enabled = supplyEnabled,
                onEnabledChange = { supplyEnabled = it },
                tag = "supply",
            )
            if (supplyEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = supplyInitialUnits,
                        onValueChange = { supplyInitialUnits = it },
                        label = "Starting amount",
                        error = errors["supplyInitialUnits"],
                        tag = "supply_initial",
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = unitsPerDose,
                        onValueChange = { unitsPerDose = it },
                        label = "Per dose",
                        error = errors["unitsPerDose"],
                        tag = "units_per_dose",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = lowSupplyThreshold,
                        onValueChange = { lowSupplyThreshold = it },
                        label = "Low at (optional)",
                        error = errors["lowSupplyThreshold"],
                        tag = "low_supply_threshold",
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = supplyUnitName,
                        onValueChange = { supplyUnitName = it.take(31) },
                        label = { Text("Unit") },
                        singleLine = true,
                        isError = "supplyUnitName" in errors,
                        modifier = Modifier.weight(1f).testTag("supply_unit_name"),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    enabled = !isSaving,
                    onClick = {
                        val draft = buildDraft() ?: return@Button
                        val runningDurationChanged =
                            medicine != null &&
                                activeCountdowns.any { countdown ->
                                    draft.isSlotEnabled(countdown.slot) &&
                                        draft.countdownMinutes(countdown.slot) !=
                                        medicine.countdownMinutes(countdown.slot)
                                }
                        if (runningDurationChanged) {
                            pendingCountdownDraft = draft
                        } else {
                            save(draft)
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("save_medicine"),
                ) {
                    Text("Save")
                }
            }
            if (medicine != null) {
                OutlinedButton(
                    onClick = {
                        onArchive(medicine.id)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Archive medicine")
                }
                TextButton(
                    onClick = { deleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete medicine", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    pendingCountdownDraft?.let { draft ->
        val stopsRunningTimer =
            activeCountdowns.any { countdown ->
                draft.isSlotEnabled(countdown.slot) &&
                    draft.countdownMinutes(countdown.slot) == null
            }
        AlertDialog(
            onDismissRequest = { pendingCountdownDraft = null },
            title = { Text("A countdown is already running") },
            text = {
                Text(
                    if (stopsRunningTimer) {
                        "Keep its original target time, or stop the affected timer " +
                            "and disable future starts."
                    } else {
                        "Keep its original target time, or restart the affected timer " +
                            "using the new duration."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCountdownDraft = null
                        save(draft.copy(restartChangedCountdowns = true))
                    },
                ) { Text(if (stopsRunningTimer) "Stop timer" else "Restart timer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingCountdownDraft = null
                        save(draft)
                    },
                ) { Text("Keep timer") }
            },
        )
    }

    if (deleteDialog && medicine != null) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete medicine?") },
            text = {
                Text(
                    "Historical dose records will remain, but this medicine cannot be restored.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(medicine.id)
                        deleteDialog = false
                        onBack()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SlotEditor(
    slot: DoseSlot,
    state: SlotEditorState,
    onStateChange: (SlotEditorState) -> Unit,
    errors: Map<String, String>,
) {
    val tag = slot.wireValue
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(
            title = "${slot.defaultLabel} slot",
            enabled = state.enabled,
            onEnabledChange = { onStateChange(state.copy(enabled = it)) },
            tag = tag,
        )
        if (state.enabled) {
            OutlinedTextField(
                value = state.label,
                onValueChange = { onStateChange(state.copy(label = it.take(61))) },
                label = { Text("Custom label") },
                singleLine = true,
                isError = errors["${tag}Label"] != null,
                supportingText = {
                    Text(errors["${tag}Label"] ?: "${state.label.length}/60")
                },
                modifier = Modifier.fillMaxWidth().testTag("${tag}_label"),
            )
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

@Suppress("FunctionNaming")
@Composable
private fun ToggleRow(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    tag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("${tag}_toggle")
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                ).semantics(mergeDescendants = true) {
                    contentDescription = title
                    role = Role.Switch
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Suppress("FunctionNaming", "CyclomaticComplexMethod")
@Composable
private fun CountdownEditor(
    minutes: Int?,
    onMinutesChange: (Int?) -> Unit,
    error: String?,
    tag: String,
) {
    val presets = listOf(30, 60, 90, 120)
    var customMode by rememberSaveable(tag, minutes) {
        mutableStateOf(minutes != null && minutes !in presets)
    }
    val title =
        minutes
            ?.let { "Meal countdown · ${CountdownLogic.formatDuration(it)}" }
            ?: "Meal countdown"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleRow(
            title = title,
            enabled = minutes != null,
            onEnabledChange = { enabled -> onMinutesChange(if (enabled) 30 else null) },
            tag = "${tag}_countdown",
        )
        if (minutes != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = minutes == preset && !customMode,
                        onClick = {
                            customMode = false
                            onMinutesChange(preset)
                        },
                        label = { Text(CountdownLogic.formatDuration(preset)) },
                    )
                }
            }
            FilterChip(
                selected = customMode,
                onClick = {
                    customMode = true
                    if (minutes in presets) onMinutesChange(45)
                },
                label = { Text("Custom duration") },
            )
            if (customMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = (minutes / 60).toString(),
                        onValueChange = { hours ->
                            val parsed = hours.filter(Char::isDigit).toIntOrNull() ?: 0
                            onMinutesChange(parsed * 60 + (minutes % 60))
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
                            onMinutesChange((minutes / 60) * 60 + parsed)
                        },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("${tag}_countdown_minutes"),
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ReminderEditor(
    slot: DoseSlot,
    minutesAfterMidnight: Int?,
    onMinutesChange: (Int?) -> Unit,
    error: String?,
) {
    val tag = slot.wireValue
    val defaultMinutes =
        when (slot) {
            DoseSlot.MORNING -> 8 * 60
            DoseSlot.AFTERNOON -> 13 * 60
            DoseSlot.EVENING -> 18 * 60
            DoseSlot.NIGHT -> 22 * 60
        }
    val title =
        minutesAfterMidnight
            ?.let(::formatClockMinutes)
            ?.let { "Reminder · $it" }
            ?: "Time reminder"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleRow(
            title = title,
            enabled = minutesAfterMidnight != null,
            onEnabledChange = { onMinutesChange(if (it) defaultMinutes else null) },
            tag = "${tag}_reminder",
        )
        if (minutesAfterMidnight != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = (minutesAfterMidnight / 60).toString().padStart(2, '0'),
                    onValueChange = { hourText ->
                        val hour =
                            hourText
                                .filter(Char::isDigit)
                                .toIntOrNull()
                                ?.coerceIn(0, 23)
                                ?: 0
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
                        val minute =
                            minuteText
                                .filter(Char::isDigit)
                                .toIntOrNull()
                                ?.coerceIn(0, 59)
                                ?: 0
                        onMinutesChange((minutesAfterMidnight / 60) * 60 + minute)
                    },
                    label = { Text("Minute") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("${tag}_reminder_minute"),
                )
            }
            Text(
                "Reminder scheduling is device-local; the medicine data still syncs to your account.",
                style = MaterialTheme.typography.bodySmall,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChange(
                it.filter { character -> character.isDigit() || character == '.' },
            )
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { message -> Text(message) } },
        modifier = modifier.testTag(tag),
    )
}

private fun parseDate(
    value: String,
    key: String,
    errors: MutableMap<String, String>,
): LocalDate? {
    if (value.isBlank()) return null
    return runCatching { LocalDate.parse(value.trim()) }
        .getOrElse {
            errors[key] = "Use YYYY-MM-DD."
            null
        }
}

private fun Double.toDisplayNumber(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun formatClockMinutes(minutesAfterMidnight: Int): String =
    "%02d:%02d".format(minutesAfterMidnight / 60, minutesAfterMidnight % 60)
