package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.DoseRow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Suppress("FunctionNaming")
@Composable
internal fun EmptyTodayCard(onAdd: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No medicines yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add a medicine, choose its daily slots, and your Today list will appear here.",
            )
            Button(onClick = onAdd) { Text("Add medicine") }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun UndoDoseDialog(
    row: DoseRow,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val wasSkipped = row.isSkipped
    val message =
        if (wasSkipped) {
            "This returns ${row.medicineName} — ${row.label} to Pending. " +
                "The skip and undo remain in history."
        } else {
            "This marks ${row.medicineName} — ${row.label} as Pending. " +
                "The original check and undo remain in history."
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (wasSkipped) "Undo skipped dose?" else "Undo this check?") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text("Undo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun SkipDoseDialog(
    row: DoseRow,
    reason: String,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip this dose?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${row.medicineName} — ${row.label} will be recorded as skipped, not taken.",
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("skip_reason"),
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Skip dose") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun TodaySummary(state: MainUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("main_loading"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Loading medicines…", style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }
            Text(
                state.logicalDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${state.progress.completed} taken · ${state.progress.skipped} skipped · " +
                    "${state.progress.pending} pending",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.progress.total > 0) {
                LinearProgressIndicator(
                    progress = {
                        state.progress.completed.toFloat() / state.progress.total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                state.hasPendingWrites -> {
                    Text(
                        "Waiting to sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                state.isCached -> {
                    Text("Showing cached data", style = MaterialTheme.typography.labelMedium)
                }
            }
            state.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal fun formatSupplyAmount(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        "%.2f".format(value).trimEnd('0').trimEnd('.')
    }

@Suppress("FunctionNaming")
@Composable
internal fun RefillSupplyDialog(
    medicine: io.github.ffelixq.medswidget.domain.Medicine,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var amount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val parsed = amount.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add supply") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${medicine.name} · current ${medicine.supplyInitialUnits ?: 0.0} ${medicine.supplyUnitName}")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        amount = value.filter { it.isDigit() || it == '.' }.take(12)
                    },
                    label = { Text("Amount to add") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null && parsed.isFinite() && parsed > 0.0,
                onClick = { parsed?.let(onConfirm) },
            ) { Text("Add refill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
