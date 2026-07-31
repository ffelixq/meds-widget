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
    onStartCountdown: (DoseRow, CheckSource) -> Unit = { _, _ -> },
    onCancelCountdown: (DoseRow) -> Unit = {},
    onRestartCountdown: (DoseRow) -> Unit = {},
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
                        onStartCountdown = { onStartCountdown(it, CheckSource.APP) },
                        onCancelCountdown = onCancelCountdown,
                        onRestartCountdown = onRestartCountdown,
                    )
                }
            }

            if (state.medicines.isNotEmpty()) {
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

@Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun MedicineCard(
    medicine: Medicine,
    rows: List<DoseRow>,
    onEdit: () -> Unit,
    onCheck: (DoseRow) -> Unit,
    onStartCountdown: (DoseRow) -> Unit,
    onCancelCountdown: (DoseRow) -> Unit,
    onRestartCountdown: (DoseRow) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Text(medicine.name, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row ->
                DoseCheckRow(
                    row = row,
                    onClick = { onCheck(row) },
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

@Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
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
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status = if (row.isTaken) "taken" else "not taken"
    val liveNow by produceState(initialValue = now, row.countdown?.targetAt) {
        while (row.countdown != null) {
            val remaining = Duration.between(Instant.now(), row.countdown.targetAt)
            if (remaining.isNegative || remaining.isZero) {
                value = Instant.now()
                break
            }
            val waitMillis =
                minOf(
                    remaining.toMillis().coerceAtLeast(1_000L),
                    60_000L,
                )
            delay(waitMillis)
            value = Instant.now()
        }
    }
    val countdown = CountdownLogic.display(row.countdownMinutes, row.countdown, liveNow)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
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
        if (!row.isTaken) {
            when (countdown.status) {
                CountdownDisplayStatus.NOT_STARTED -> {
                    onStartCountdown?.let { start ->
                        TextButton(
                            onClick = start,
                            modifier = Modifier.testTag("start_countdown_${row.stateId}"),
                        ) {
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
                            if (it.logicalDay != row.stateId.take(10).let(java.time.LocalDate::parse)) {
                                Text("From ${it.logicalDay}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (showCountdownManagement) {
                            Row {
                                TextButton(onClick = { onCancelCountdown?.invoke() }) { Text("Cancel") }
                                TextButton(onClick = { onRestartCountdown?.invoke() }) { Text("Restart") }
                            }
                        }
                    }
                }

                else -> {
                    Spacer(Modifier)
                }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Widget previews", style = MaterialTheme.typography.titleLarge)
        Text(
            "These are live previews using your current account data.",
            style = MaterialTheme.typography.bodySmall,
        )
        val firstMedicine = state.medicines.first()
        val compactSpec = WidgetLayoutSpec.forSize(DpSize(130.dp, 130.dp), WidgetKind.SINGLE)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Compact single · 2×2 preview", style = MaterialTheme.typography.labelLarge)
                Text(firstMedicine.name, fontSize = compactSpec.titleSp.sp)
                state.rows.filter { it.medicineId == firstMedicine.id }.forEach { row ->
                    WidgetPreviewDoseRow(
                        row = row,
                        spec = compactSpec,
                        onCheck = { onCheck(row) },
                        onStartCountdown = { onStartCountdown(row) },
                        modifier = Modifier.testTag("preview_single_${row.stateId}"),
                    )
                }
            }
        }
        val standardSpec = WidgetLayoutSpec.forSize(DpSize(190.dp, 145.dp), WidgetKind.SINGLE)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(standardSpec.outerPaddingDp.dp)) {
                Text("Standard single · physical-size preview", style = MaterialTheme.typography.labelLarge)
                Text(firstMedicine.name, fontSize = standardSpec.titleSp.sp)
                state.rows.filter { it.medicineId == firstMedicine.id }.forEach { row ->
                    WidgetPreviewDoseRow(
                        row = row,
                        spec = standardSpec,
                        onCheck = { onCheck(row) },
                        onStartCountdown = { onStartCountdown(row) },
                        modifier = Modifier.testTag("preview_standard_${row.stateId}"),
                    )
                }
            }
        }
        val allSpec = WidgetLayoutSpec.forSize(DpSize(320.dp, 160.dp), WidgetKind.ALL)
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
                    Text(row.medicineName, fontSize = allSpec.supportingSp.sp)
                    WidgetPreviewDoseRow(
                        row = row,
                        spec = allSpec,
                        onCheck = { onCheck(row) },
                        onStartCountdown = { onStartCountdown(row) },
                        modifier = Modifier.testTag("preview_all_${row.stateId}"),
                    )
                }
            }
        }
        WidgetStateExamples(standardSpec)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WidgetPreviewDoseRow(
    row: DoseRow,
    spec: WidgetLayoutSpec,
    onCheck: () -> Unit,
    onStartCountdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val countdown = CountdownLogic.display(row.countdownMinutes, row.countdown, Instant.now())
    Row(
        modifier = Modifier.fillMaxWidth().height(spec.rowHeightDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                modifier
                    .weight(1f)
                    .height(spec.rowHeightDp.dp)
                    .clickable(
                        enabled = !row.isTaken,
                        role = Role.Checkbox,
                        onClick = onCheck,
                    ).semantics(mergeDescendants = true) {
                        role = Role.Checkbox
                        contentDescription =
                            "${row.medicineName}, ${row.label}, " +
                            if (row.isTaken) "taken" else "not taken"
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (row.isTaken) "☑" else "☐",
                fontSize = spec.checkSp.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(row.label, fontSize = spec.bodySp.sp, maxLines = 1, modifier = Modifier.weight(1f))
            row.checkedAt?.let {
                Text(
                    TimeFormatting.compact(context, it, row.checkedTimezone),
                    fontSize = spec.supportingSp.sp,
                    maxLines = 1,
                )
            }
        }
        if (!row.isTaken && countdown.text != null) {
            Text(
                countdown.text,
                fontSize = spec.supportingSp.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier =
                    Modifier
                        .height(spec.rowHeightDp.dp)
                        .clickable(
                            enabled = countdown.status == CountdownDisplayStatus.NOT_STARTED,
                            onClick = onStartCountdown,
                        ).padding(horizontal = 6.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription =
                                if (countdown.status == CountdownDisplayStatus.NOT_STARTED) {
                                    "Start ${row.label} countdown"
                                } else {
                                    "${row.label} countdown ${countdown.text}"
                                }
                        },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WidgetStateExamples(spec: WidgetLayoutSpec) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spec.outerPaddingDp.dp)) {
            Text("Countdown state examples", fontSize = spec.titleSp.sp)
            listOf(
                "☐ After lunch    Start 2h",
                "☐ After lunch    1h 42m",
                "☐ After lunch    READY",
                "☑ After lunch    2:14 PM",
            ).forEach { value ->
                Text(
                    value,
                    fontSize = spec.bodySp.sp,
                    modifier = Modifier.heightIn(min = spec.rowHeightDp.dp),
                )
            }
        }
    }
}
