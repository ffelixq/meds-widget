package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.util.TimeFormatting
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun MainScreen(
    state: MainUiState,
    onCheck: (DoseRow, CheckSource) -> Unit,
    onUndo: (DoseRow) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    var undoCandidate by remember { mutableStateOf<DoseRow?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meds Widget") },
                actions = {
                    TextButton(onClick = onHistory) { Text("History") }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add medicine")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (state.isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("main_loading"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )
                        Text("Loading medicines…", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(
                        state.logicalDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(state.progress.display, style = MaterialTheme.typography.bodyMedium)
                    if (state.hasPendingWrites) {
                        Text(
                            "Waiting to sync",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (state.isCached) {
                        Text("Showing cached data", style = MaterialTheme.typography.labelMedium)
                    }
                    state.errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (!state.isLoading && state.medicines.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No medicines yet", style = MaterialTheme.typography.titleMedium)
                            Text("Add a medicine and choose at least one afternoon or night slot.")
                            Button(onClick = onAdd) { Text("Add medicine") }
                        }
                    }
                }
            }

            state.medicines.forEach { medicine ->
                item(key = "medicine_${medicine.id}") {
                    MedicineCard(
                        medicine = medicine,
                        rows = state.rows.filter { it.medicineId == medicine.id },
                        onEdit = { onEdit(medicine) },
                        onCheck = { row ->
                            if (row.isTaken) undoCandidate = row else onCheck(row, CheckSource.APP)
                        },
                    )
                }
            }

            if (state.medicines.isNotEmpty()) {
                item {
                    WidgetPreviews(
                        state = state,
                        onCheck = { row -> onCheck(row, CheckSource.APP_PREVIEW) },
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    undoCandidate?.let { row ->
        AlertDialog(
            onDismissRequest = { undoCandidate = null },
            title = { Text("Undo this check?") },
            text = {
                Text(
                    "This will mark ${row.medicineName} — ${row.label} as unchecked. " +
                        "The original check and this undo remain in history.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUndo(row)
                        undoCandidate = null
                    },
                ) {
                    Text("Undo check")
                }
            },
            dismissButton = {
                TextButton(onClick = { undoCandidate = null }) { Text("Cancel") }
            },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun MedicineCard(
    medicine: Medicine,
    rows: List<DoseRow>,
    onEdit: () -> Unit,
    onCheck: (DoseRow) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Text(medicine.name, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row ->
                DoseCheckRow(
                    row = row,
                    onClick = { onCheck(row) },
                    modifier = Modifier.testTag("app_dose_${row.stateId}"),
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun DoseCheckRow(
    row: DoseRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    allowUndo: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status = if (row.isTaken) "taken" else "not taken"
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 4.dp)
                .clickable(enabled = !row.isTaken || allowUndo, role = Role.Checkbox, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${row.medicineName}, ${row.label}, $status"
                    role = Role.Checkbox
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = row.isTaken,
            onCheckedChange = null,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(row.label, style = MaterialTheme.typography.bodyLarge)
            row.checkedAt?.let {
                Text(
                    "Checked ${TimeFormatting.compact(context, it, row.checkedTimezone)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WidgetPreviews(
    state: MainUiState,
    onCheck: (DoseRow) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Widget previews", style = MaterialTheme.typography.titleLarge)
        Text(
            "These are live previews using your current account data.",
            style = MaterialTheme.typography.bodySmall,
        )
        val firstMedicine = state.medicines.first()
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Single medicine · 2×2 preview", style = MaterialTheme.typography.labelLarge)
                Text(firstMedicine.name, style = MaterialTheme.typography.titleMedium)
                state.rows.filter { it.medicineId == firstMedicine.id }.forEach { row ->
                    DoseCheckRow(
                        row = row,
                        onClick = { if (!row.isTaken) onCheck(row) },
                        modifier = Modifier.testTag("preview_single_${row.stateId}"),
                        allowUndo = false,
                    )
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .height(230.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                        .testTag("preview_all_list"),
            ) {
                Text(
                    "All medicines · 4×2 preview  ${state.progress.compactDisplay}",
                    style = MaterialTheme.typography.labelLarge,
                )
                state.rows.forEach { row ->
                    Text(row.medicineName, style = MaterialTheme.typography.labelMedium)
                    DoseCheckRow(
                        row = row,
                        onClick = { if (!row.isTaken) onCheck(row) },
                        modifier = Modifier.testTag("preview_all_${row.stateId}"),
                        allowUndo = false,
                    )
                }
            }
        }
    }
}
