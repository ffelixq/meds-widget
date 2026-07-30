package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.ValidationResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun MedicineScreen(
    medicine: Medicine?,
    onBack: () -> Unit,
    onSave: suspend (MedicineDraft) -> ValidationResult,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
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
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isSaving by remember { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
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
                tag = "afternoon",
            )
            SlotEditor(
                title = "Night slot",
                enabled = nightEnabled,
                onEnabledChange = { nightEnabled = it },
                label = nightLabel,
                onLabelChange = { nightLabel = it.take(61) },
                error = errors["nightLabel"],
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
                        scope.launch {
                            val result =
                                onSave(
                                    MedicineDraft(
                                        id = medicine?.id,
                                        name = name,
                                        afternoonEnabled = afternoonEnabled,
                                        afternoonLabel = afternoonLabel,
                                        nightEnabled = nightEnabled,
                                        nightLabel = nightLabel,
                                    ),
                                )
                            errors = result.errors
                            isSaving = false
                            if (result.isValid) onBack()
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

@Suppress("FunctionNaming")
@Composable
private fun SlotEditor(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    error: String?,
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
        }
    }
}
