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
import kotlinx.coroutines.launch

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
    var afternoonEnabled by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.afternoonEnabled ?: true)
    }
    var afternoonLabel by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.afternoonLabel ?: "Afternoon")
    }
    var nightEnabled by rememberSaveable(medicine?.id) { mutableStateOf(medicine?.nightEnabled ?: true) }
    var nightLabel by rememberSaveable(medicine?.id) { mutableStateOf(medicine?.nightLabel ?: "Night") }
    var afternoonCountdownMinutes by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.afternoonCountdownMinutes)
    }
    var nightCountdownMinutes by rememberSaveable(medicine?.id) {
        mutableStateOf(medicine?.nightCountdownMinutes)
    }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isSaving by remember { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCountdownDraft by remember { mutableStateOf<MedicineDraft?>(null) }
    val scope = rememberCoroutineScope()

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(101) },
                label = { Text("Medicine name") },
                singleLine = true,
                isError = "name" in errors,
                supportingText = { Text(errors["name"] ?: "${name.length}/100") },
                modifier = Modifier.fillMaxWidth().testTag("medicine_name"),
            )
            SlotEditor(
                title = "Afternoon slot",
                enabled = afternoonEnabled,
                onEnabledChange = { afternoonEnabled = it },
                label = afternoonLabel,
                onLabelChange = { afternoonLabel = it.take(61) },
                error = errors["afternoonLabel"],
                countdownMinutes = afternoonCountdownMinutes,
                onCountdownChange = { afternoonCountdownMinutes = it },
                countdownError = errors["afternoonCountdownMinutes"],
                tag = "afternoon",
            )
            SlotEditor(
                title = "Night slot",
                enabled = nightEnabled,
                onEnabledChange = { nightEnabled = it },
                label = nightLabel,
                onLabelChange = { nightLabel = it.take(61) },
                error = errors["nightLabel"],
                countdownMinutes = nightCountdownMinutes,
                onCountdownChange = { nightCountdownMinutes = it },
                countdownError = errors["nightCountdownMinutes"],
                tag = "night",
            )
            errors["slots"]?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    enabled = !isSaving,
                    onClick = {
                        isSaving = true
                        val draft =
                            MedicineDraft(
                                id = medicine?.id,
                                name = name,
                                afternoonEnabled = afternoonEnabled,
                                afternoonLabel = afternoonLabel,
                                afternoonCountdownMinutes = afternoonCountdownMinutes,
                                nightEnabled = nightEnabled,
                                nightLabel = nightLabel,
                                nightCountdownMinutes = nightCountdownMinutes,
                            )
                        val runningDurationChanged =
                            medicine != null &&
                                activeCountdowns.any { countdown ->
                                    val slotRemainsEnabled =
                                        when (countdown.slot) {
                                            DoseSlot.AFTERNOON -> afternoonEnabled
                                            DoseSlot.NIGHT -> nightEnabled
                                        }
                                    val next =
                                        if (countdown.slot == DoseSlot.AFTERNOON) {
                                            afternoonCountdownMinutes
                                        } else {
                                            nightCountdownMinutes
                                        }
                                    slotRemainsEnabled && next != medicine.countdownMinutes(countdown.slot)
                                }
                        if (runningDurationChanged) {
                            isSaving = false
                            pendingCountdownDraft = draft
                        } else {
                            scope.launch {
                                val result = onSave(draft)
                                errors = result.errors
                                isSaving = false
                                if (result.isValid) onBack()
                            }
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
                TextButton(onClick = { deleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete medicine", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    pendingCountdownDraft?.let { draft ->
        val stopsRunningTimer =
            activeCountdowns.any { countdown ->
                when (countdown.slot) {
                    DoseSlot.AFTERNOON -> draft.afternoonEnabled && draft.afternoonCountdownMinutes == null
                    DoseSlot.NIGHT -> draft.nightEnabled && draft.nightCountdownMinutes == null
                }
            }
        AlertDialog(
            onDismissRequest = { pendingCountdownDraft = null },
            title = { Text("A countdown is already running") },
            text = {
                Text(
                    if (stopsRunningTimer) {
                        "Keep its original target time, or stop the affected timer and disable future starts. " +
                            "This is a personal timer, not medical advice."
                    } else {
                        "Keep its original target time, or restart the affected timer using the new duration. " +
                            "This is a personal timer, not medical advice."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCountdownDraft = null
                        isSaving = true
                        scope.launch {
                            val result = onSave(draft.copy(restartChangedCountdowns = true))
                            errors = result.errors
                            isSaving = false
                            if (result.isValid) onBack()
                        }
                    },
                ) { Text(if (stopsRunningTimer) "Stop timer" else "Restart timer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingCountdownDraft = null
                        isSaving = true
                        scope.launch {
                            val result = onSave(draft)
                            errors = result.errors
                            isSaving = false
                            if (result.isValid) onBack()
                        }
                    },
                ) { Text("Keep timer") }
            },
        )
    }
    if (deleteDialog && medicine != null) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete medicine?") },
            text = { Text("Historical dose records will remain, but this medicine cannot be restored.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(medicine.id)
                        deleteDialog = false
                        onBack()
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SlotEditor(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    error: String?,
    countdownMinutes: Int?,
    onCountdownChange: (Int?) -> Unit,
    countdownError: String?,
    tag: String,
) {
    Column {
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
        if (enabled) {
            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                label = { Text("Custom label") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "${label.length}/60") },
                modifier = Modifier.fillMaxWidth().testTag("${tag}_label"),
            )
            CountdownEditor(
                minutes = countdownMinutes,
                onMinutesChange = onCountdownChange,
                error = countdownError,
                tag = tag,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("${tag}_countdown_toggle")
                    .toggleable(
                        value = minutes != null,
                        role = Role.Switch,
                        onValueChange = { enabled -> onMinutesChange(if (enabled) 30 else null) },
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Meal countdown", style = MaterialTheme.typography.titleSmall)
                Text(
                    minutes?.let { "Wait ${CountdownLogic.formatDuration(it)} after food" }
                        ?: "Optional personal timer",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = minutes != null, onCheckedChange = null)
        }
        if (minutes != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.take(2).forEach { preset ->
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.drop(2).forEach { preset ->
                    FilterChip(
                        selected = minutes == preset && !customMode,
                        onClick = {
                            customMode = false
                            onMinutesChange(preset)
                        },
                        label = { Text(CountdownLogic.formatDuration(preset)) },
                    )
                }
                FilterChip(
                    selected = customMode,
                    onClick = {
                        customMode = true
                        if (minutes in presets) onMinutesChange(45)
                    },
                    label = { Text("Custom") },
                )
            }
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
