package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.util.TimeFormatting
import io.github.ffelixq.medswidget.widget.WidgetKind
import io.github.ffelixq.medswidget.widget.WidgetLayoutSpec
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
fun MainScreen(
    state: MainUiState,
    onCheck: (DoseRow, CheckSource) -> Unit,
    onUndo: (DoseRow) -> Unit,
    onSkip: (DoseRow, String) -> Unit = { _, _ -> },
    onStartCountdown: (DoseRow, CheckSource) -> Unit = { _, _ -> },
    onCancelCountdown: (DoseRow) -> Unit = {},
    onRestartCountdown: (DoseRow) -> Unit = {},
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    var undoCandidate by remember { mutableStateOf<DoseRow?>(null) }
    var skipCandidate by remember { mutableStateOf<DoseRow?>(null) }
    var skipReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TodaySummary(state)
            }

            if (!state.isLoading && state.medicines.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No medicines yet", style = MaterialTheme.typography.titleMedium)
                            Text("Add a medicine, choose its daily slots, and your Today list will appear here.")
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
                        logicalDay = state.logicalDay,
                        onEdit = { onEdit(medicine) },
                        onCheck = { row ->
                            if (row.isTaken || row.isSkipped) {
                                undoCandidate = row
                            } else {
                                onCheck(row, CheckSource.APP)
                            }
                        },
                        onSkip = { row ->
                            skipReason = ""
                            skipCandidate = row
                        },
                        onStartCountdown = { onStartCountdown(it, CheckSource.APP) },
                        onCancelCountdown = onCancelCountdown,
                        onRestartCountdown = onRestartCountdown,
                    )
                }
            }

            if (state.rows.isNotEmpty()) {
                item {
                    WidgetPreviews(
                        state = state,
                        onCheck = { row -> onCheck(row, CheckSource.APP_PREVIEW) },
                        onStartCountdown = { row -> onStartCountdown(row, CheckSource.APP_PREVIEW) },
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    undoCandidate?.let { row ->
        val wasSkipped = row.isSkipped
        AlertDialog(
            onDismissRequest = { undoCandidate = null },
            title = { Text(if (wasSkipped) "Undo skipped dose?" else "Undo this check?") },
            text = {
                Text(
                    if (wasSkipped) {
                        "This returns ${row.medicineName} — ${row.label} to Pending. The skip and undo remain in history."
                    } else {
                        "This marks ${row.medicineName} — ${row.label} as Pending. The original check and undo remain in history."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUndo(row)
                        undoCandidate = null
                    },
                ) { Text("Undo") }
            },
            dismissButton = { TextButton(onClick = { undoCandidate = null }) { Text("Cancel") } },
        )
    }

    skipCandidate?.let { row ->
        AlertDialog(
            onDismissRequest = { skipCandidate = null },
            title = { Text("Skip this dose?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${row.medicineName} — ${row.label} will be recorded as skipped, not taken.")
                    OutlinedTextField(
                        value = skipReason,
                        onValueChange = { skipReason = it.take(120) },
                        label = { Text("Reason (optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("skip_reason"),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSkip(row, skipReason)
                        skipCandidate = null
                        skipReason = ""
                    },
                ) { Text("Skip dose") }
            },
            dismissButton = { TextButton(onClick = { skipCandidate = null }) { Text("Cancel") } },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TodaySummary(state: MainUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                "${state.progress.completed} taken · ${state.progress.skipped} skipped · ${state.progress.pending} pending",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.progress.total > 0) {
                LinearProgressIndicator(
                    progress = { state.progress.completed.toFloat() / state.progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.hasPendingWrites) {
                Text("Waiting to sync", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else if (state.isCached) {
                Text("Showing cached data", style = MaterialTheme.typography.labelMedium)
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun MedicineCard(
    medicine: Medicine,
    rows: List<DoseRow>,
    logicalDay: java.time.LocalDate,
    onEdit: () -> Unit,
    onCheck: (DoseRow) -> Unit,
    onSkip: (DoseRow) -> Unit,
    onStartCountdown: (DoseRow) -> Unit,
    onCancelCountdown: (DoseRow) -> Unit,
    onRestartCountdown: (DoseRow) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(medicine.name, style = MaterialTheme.typography.titleMedium)
                    if (medicine.nickname.isNotBlank()) {
                        Text(medicine.nickname, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            when {
                medicine.startDate?.isAfter(logicalDay) == true -> {
                    Text("Starts ${medicine.startDate}", style = MaterialTheme.typography.bodyMedium)
                }
                medicine.endDate?.isBefore(logicalDay) == true -> {
                    Text("Course completed ${medicine.endDate}", style = MaterialTheme.typography.bodyMedium)
                }
                rows.isEmpty() -> {
                    Text("No doses scheduled today", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    rows.forEach { row ->
                        DoseCheckRow(
                            row = row,
                            onClick = { onCheck(row) },
                            onSkip = { onSkip(row) },
                            onStartCountdown = { onStartCountdown(row) },
                            onCancelCountdown = { onCancelCountdown(row) },
                            onRestartCountdown = { onRestartCountdown(row) },
                            showCountdownManagement = true,
                            modifier = Modifier.testTag("app_dose_${row.stateId}"),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "CyclomaticComplexMethod")
@Composable
fun DoseCheckRow(
    row: DoseRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    allowUndo: Boolean = true,
    onStartCountdown: (() -> Unit)? = null,
    onCancelCountdown: (() -> Unit)? = null,
    onRestartCountdown: (() -> Unit)? = null,
    showCountdownManagement: Boolean = false,
    now: Instant = Instant.now(),
    onSkip: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status =
        when {
            row.isTaken -> "taken"
            row.isSkipped -> "skipped"
            else -> "pending"
        }
    val liveNow by produceState(initialValue = now, row.countdown?.targetAt) {
        while (row.countdown != null) {
            val remaining = Duration.between(Instant.now(), row.countdown.targetAt)
            if (remaining.isNegative || remaining.isZero) {
                value = Instant.now()
                break
            }
            delay(minOf(remaining.toMillis().coerceAtLeast(1_000L), 60_000L))
            value = Instant.now()
        }
    }
    val countdown = CountdownLogic.display(row.countdownMinutes, row.countdown, liveNow)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(
                        enabled = (!row.isTaken && !row.isSkipped) || allowUndo,
                        role = Role.Checkbox,
                        onClick = onClick,
                    ).semantics(mergeDescendants = true) {
                        contentDescription = "${row.medicineName}, ${row.label}, $status"
                        role = Role.Checkbox
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = row.isTaken, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                when {
                    row.isTaken && row.checkedAt != null -> {
                        Text(
                            "Taken ${TimeFormatting.compact(context, row.checkedAt, row.checkedTimezone)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    row.isSkipped -> {
                        Text(
                            row.skipReason?.let { "Skipped · $it" } ?: "Skipped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
        if (!row.isTaken && !row.isSkipped) {
            when (countdown.status) {
                CountdownDisplayStatus.NOT_STARTED -> {
                    onStartCountdown?.let { start ->
                        TextButton(onClick = start, modifier = Modifier.testTag("start_countdown_${row.stateId}")) {
                            Text(countdown.text.orEmpty())
                        }
                    }
                }
                CountdownDisplayStatus.RUNNING,
                CountdownDisplayStatus.READY,
                -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            countdown.text.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        row.countdown?.let {
                            Text(
                                "Ready ${TimeFormatting.compact(context, it.targetAt, it.startedTimezone)}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (showCountdownManagement) {
                            Row {
                                TextButton(onClick = { onCancelCountdown?.invoke() }) { Text("Cancel") }
                                TextButton(onClick = { onRestartCountdown?.invoke() }) { Text("Restart") }
                            }
                        }
                    }
                }
                else -> Unit
            }
            if (onSkip != null && countdown.status != CountdownDisplayStatus.RUNNING) {
                TextButton(onClick = onSkip, modifier = Modifier.testTag("skip_${row.stateId}")) { Text("Skip") }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WidgetPreviews(
    state: MainUiState,
    onCheck: (DoseRow) -> Unit,
    onStartCountdown: (DoseRow) -> Unit,
) {
    val firstMedicine = state.medicines.firstOrNull { medicine -> state.rows.any { it.medicineId == medicine.id } } ?: return
    val rows = state.rows.filter { it.medicineId == firstMedicine.id }
    val singleSpec = WidgetLayoutSpec.forSize(DpSize(190.dp, 145.dp), WidgetKind.SINGLE)
    val allSpec = WidgetLayoutSpec.forSize(DpSize(320.dp, 160.dp), WidgetKind.ALL)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Widget previews", style = MaterialTheme.typography.titleLarge)
        Text("Live previews use the same dose actions as the real widgets.", style = MaterialTheme.typography.bodySmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("2×2 · ${firstMedicine.widgetDisplayName()}", fontSize = singleSpec.titleSp.sp)
                rows.forEach { row ->
                    PreviewRow(row, singleSpec, { onCheck(row) }, { onStartCountdown(row) })
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("4×2 · ${state.progress.compactDisplay}", fontSize = allSpec.titleSp.sp)
                state.rows.take(4).forEach { row ->
                    Text(
                        state.medicines.firstOrNull { it.id == row.medicineId }?.widgetDisplayName() ?: "Medicine",
                        fontSize = allSpec.supportingSp.sp,
                    )
                    PreviewRow(row, allSpec, { onCheck(row) }, { onStartCountdown(row) })
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PreviewRow(
    row: DoseRow,
    spec: WidgetLayoutSpec,
    onCheck: () -> Unit,
    onStartCountdown: () -> Unit,
) {
    val countdown = CountdownLogic.display(row.countdownMinutes, row.countdown, Instant.now())
    Row(
        modifier = Modifier.fillMaxWidth().height(spec.rowHeightDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (row.isTaken) "☑" else if (row.isSkipped) "–" else "☐",
            fontSize = spec.checkSp.sp,
            modifier = Modifier.clickable(enabled = !row.isTaken && !row.isSkipped, onClick = onCheck),
        )
        Spacer(Modifier.width(6.dp))
        Text(row.label, fontSize = spec.bodySp.sp, modifier = Modifier.weight(1f))
        if (!row.isTaken && !row.isSkipped && countdown.status == CountdownDisplayStatus.NOT_STARTED) {
            TextButton(onClick = onStartCountdown) { Text(countdown.text.orEmpty()) }
        } else if (!row.isTaken && !row.isSkipped) {
            Text(countdown.text.orEmpty(), fontSize = spec.supportingSp.sp)
        }
    }
}
