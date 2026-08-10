package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownDisplay
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleGroupedRow
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill
import io.github.ffelixq.medswidget.util.TimeFormatting
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

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
    onRefill: (Medicine, Double) -> Unit = { _, _ -> },
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    var undoCandidate by remember { mutableStateOf<DoseRow?>(null) }
    var skipCandidate by remember { mutableStateOf<DoseRow?>(null) }
    var skipReason by remember { mutableStateOf("") }
    var refillCandidate by remember { mutableStateOf<Medicine?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = onHistory) {
                        Text("History", fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        scrolledContainerColor =
                            MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                    ),
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(
                    onClick = onAdd,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add medicine")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AppleLargeTitle(
                    title = "Today",
                    subtitle = "Your medication plan, at a glance.",
                )
            }
            item { TodaySummary(state) }
            if (!state.isLoading && state.medicines.isEmpty()) {
                item { EmptyTodayCard(onAdd) }
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
                        onRefill = { refillCandidate = medicine },
                    )
                }
            }
            if (state.rows.isNotEmpty()) {
                item {
                    WidgetPreviews(
                        state = state,
                        onCheck = { row -> onCheck(row, CheckSource.APP_PREVIEW) },
                        onStartCountdown = { row ->
                            onStartCountdown(row, CheckSource.APP_PREVIEW)
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    undoCandidate?.let { row ->
        UndoDoseDialog(
            row = row,
            onDismiss = { undoCandidate = null },
            onConfirm = {
                onUndo(row)
                undoCandidate = null
            },
        )
    }
    refillCandidate?.let { medicine ->
        RefillSupplyDialog(
            medicine = medicine,
            onDismiss = { refillCandidate = null },
            onConfirm = { units ->
                onRefill(medicine, units)
                refillCandidate = null
            },
        )
    }
    skipCandidate?.let { row ->
        SkipDoseDialog(
            row = row,
            reason = skipReason,
            onReasonChange = { skipReason = it.take(120) },
            onDismiss = { skipCandidate = null },
            onConfirm = {
                onSkip(row, skipReason)
                skipCandidate = null
                skipReason = ""
            },
        )
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
    onRefill: () -> Unit,
) {
    AppleCard {
        MedicineHeader(medicine, onEdit)
        if (medicine.supplyEnabled && medicine.supplyInitialUnits != null) {
            AppleGroupedRow {
                Column(Modifier.weight(1f)) {
                    val amount = formatSupplyAmount(medicine.supplyInitialUnits)
                    Text(
                        "$amount ${medicine.supplyUnitName} remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (
                        medicine.lowSupplyThreshold != null &&
                        medicine.supplyInitialUnits <= medicine.lowSupplyThreshold
                    ) {
                        Text(
                            "Supply is below your saved threshold",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (
                    medicine.lowSupplyThreshold != null &&
                    medicine.supplyInitialUnits <= medicine.lowSupplyThreshold
                ) {
                    AppleStatusPill(
                        text = "Low",
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onRefill) { Text("Refill") }
            }
        }
        when {
            medicine.startDate?.isAfter(logicalDay) == true -> {
                AppleGroupedRow {
                    Text(
                        "Starts ${medicine.startDate}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            medicine.endDate?.isBefore(logicalDay) == true -> {
                AppleGroupedRow {
                    Text(
                        "Course completed ${medicine.endDate}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            rows.isEmpty() -> {
                AppleGroupedRow {
                    Text(
                        "No doses scheduled today",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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

@Suppress("FunctionNaming")
@Composable
private fun MedicineHeader(
    medicine: Medicine,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                medicine.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (medicine.nickname.isNotBlank()) {
                Text(
                    medicine.nickname,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        ) {
            Text(
                "Edit",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
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
    val liveNow by countdownClock(row, now)
    val countdown = CountdownLogic.display(row.countdownMinutes, row.countdown, liveNow)
    val containerColor =
        when {
            row.isTaken -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            row.isSkipped -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DosePrimaryContent(
                row = row,
                onClick = onClick,
                allowUndo = allowUndo,
                modifier = modifier.weight(1f),
            )
            if (!row.isTaken && !row.isSkipped) {
                DoseTrailingActions(
                    row = row,
                    countdown = countdown,
                    onStartCountdown = onStartCountdown,
                    onCancelCountdown = onCancelCountdown,
                    onRestartCountdown = onRestartCountdown,
                    showCountdownManagement = showCountdownManagement,
                    onSkip = onSkip,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun countdownClock(
    row: DoseRow,
    initialNow: Instant,
) = produceState(initialValue = initialNow, row.countdown?.targetAt) {
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

@Suppress("FunctionNaming")
@Composable
private fun DosePrimaryContent(
    row: DoseRow,
    onClick: () -> Unit,
    allowUndo: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val status =
        when {
            row.isTaken -> "taken"
            row.isSkipped -> "skipped"
            else -> "pending"
        }
    Row(
        modifier =
            modifier
                .heightIn(min = 50.dp)
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
        DoseStatusMark(row)
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            DoseStatusText(row, context)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun DoseStatusMark(row: DoseRow) {
    val backgroundColor: Color
    val foregroundColor: Color
    val mark: String
    when {
        row.isTaken -> {
            backgroundColor = MaterialTheme.colorScheme.secondary
            foregroundColor = MaterialTheme.colorScheme.onSecondary
            mark = "✓"
        }

        row.isSkipped -> {
            backgroundColor = MaterialTheme.colorScheme.tertiary
            foregroundColor = MaterialTheme.colorScheme.onTertiary
            mark = "–"
        }

        else -> {
            backgroundColor = MaterialTheme.colorScheme.surface
            foregroundColor = MaterialTheme.colorScheme.onSurfaceVariant
            mark = ""
        }
    }
    Box(
        modifier =
            Modifier
                .size(30.dp)
                .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (mark.isNotEmpty()) {
            Text(
                mark,
                color = foregroundColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline,
                    ),
            ) {}
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun DoseStatusText(
    row: DoseRow,
    context: android.content.Context,
) {
    when {
        row.isTaken && row.checkedAt != null -> {
            val checkedTime =
                TimeFormatting.compact(context, row.checkedAt, row.checkedTimezone)
            Text(
                "Taken $checkedTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun DoseTrailingActions(
    row: DoseRow,
    countdown: CountdownDisplay,
    onStartCountdown: (() -> Unit)?,
    onCancelCountdown: (() -> Unit)?,
    onRestartCountdown: (() -> Unit)?,
    showCountdownManagement: Boolean,
    onSkip: (() -> Unit)?,
) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.End) {
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
                val ready = countdown.status == CountdownDisplayStatus.READY
                AppleStatusPill(
                    text = countdown.text.orEmpty(),
                    containerColor =
                        if (ready) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        },
                    contentColor =
                        if (ready) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
                row.countdown?.let { activeCountdown ->
                    val readyTime =
                        TimeFormatting.compact(
                            context,
                            activeCountdown.targetAt,
                            activeCountdown.startedTimezone,
                        )
                    Text(
                        "Ready $readyTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showCountdownManagement) {
                    Row {
                        TextButton(onClick = { onCancelCountdown?.invoke() }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = { onRestartCountdown?.invoke() }) {
                            Text("Restart")
                        }
                    }
                }
            }

            else -> Unit
        }
        if (onSkip != null && countdown.status != CountdownDisplayStatus.RUNNING) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("skip_${row.stateId}"),
            ) {
                Text("Skip", color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
