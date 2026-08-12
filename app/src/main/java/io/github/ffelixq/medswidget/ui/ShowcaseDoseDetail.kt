package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CountdownDisplay
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.util.TimeFormatting

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun ShowcaseDoseDetailScreen(
    row: DoseRow,
    medicine: Medicine?,
    patientMode: Boolean = false,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onUndo: () -> Unit,
    onSkip: () -> Unit,
    onRemindLater: () -> Unit,
    onStartCountdown: () -> Unit,
    onCancelCountdown: () -> Unit,
    onRestartCountdown: () -> Unit,
) {
    val countdown = showcaseCountdown(row)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        }
        item { ShowcaseDetailHeader(row, countdown) }
        item {
            ShowcaseDetailPrimaryActions(
                row = row,
                patientMode = patientMode,
                onCheck = onCheck,
                onUndo = onUndo,
                onSkip = onSkip,
                onRemindLater = onRemindLater,
            )
        }
        if (!row.isTaken && !row.isSkipped && row.countdownMinutes != null) {
            item {
                ShowcaseTimerCard(
                    row = row,
                    countdown = countdown,
                    onStart = onStartCountdown,
                    onCancel = onCancelCountdown,
                    onRestart = onRestartCountdown,
                )
            }
        }
        if (!patientMode || medicine?.notes?.isNotBlank() == true) {
            item { ShowcaseDetailsCard(row, medicine, patientMode) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailHeader(
    row: DoseRow,
    countdown: CountdownDisplay,
) {
    val status =
        when {
            row.isTaken -> "Taken"
            row.isSkipped -> "Not taking this dose"
            countdown.status == CountdownDisplayStatus.READY -> "You can take it now"
            countdown.status == CountdownDisplayStatus.RUNNING -> "Wait ${countdown.text.orEmpty()}"
            else -> "Not recorded yet"
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "${row.medicineName}, ${row.label}"
                    stateDescription = status
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShowcaseDetailStatusCircle(row)
        Text(
            row.medicineName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            row.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            status,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color =
                when {
                    row.isTaken -> MaterialTheme.colorScheme.secondary
                    row.isSkipped -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ShowcaseDetailPrimaryActions(
    row: DoseRow,
    patientMode: Boolean,
    onCheck: () -> Unit,
    onUndo: () -> Unit,
    onSkip: () -> Unit,
    onRemindLater: () -> Unit,
) {
    if (row.isTaken || row.isSkipped) {
        OutlinedButton(
            onClick = onUndo,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (row.isTaken) "I did not take it" else "Change this record")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("✓  I TOOK IT", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onRemindLater,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Remind me later")
            }
            TextButton(
                onClick = onSkip,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (patientMode) 58.dp else 52.dp),
            ) {
                Text("I am not taking this dose")
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ShowcaseTimerCard(
    row: DoseRow,
    countdown: CountdownDisplay,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRestart: () -> Unit,
) {
    AppleCard {
        AppleSectionHeader(
            title = "Wait timer",
            supportingText = "Set for ${CountdownLogic.formatDuration(row.countdownMinutes ?: 1)}.",
        )
        when (countdown.status) {
            CountdownDisplayStatus.NOT_STARTED -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                ) {
                    Text(countdown.text ?: "Start wait timer")
                }
            }

            CountdownDisplayStatus.RUNNING -> {
                Text(
                    "Wait ${countdown.text.orEmpty()}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                ShowcaseTimerControls(onCancel, onRestart)
            }

            CountdownDisplayStatus.READY -> {
                Text(
                    "You can take it now",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                ShowcaseTimerControls(onCancel, onRestart)
            }

            else -> {
                Unit
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseTimerControls(
    onCancel: () -> Unit,
    onRestart: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
        ) {
            Text("Cancel timer")
        }
        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
        ) {
            Text("Restart")
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailStatusCircle(row: DoseRow) {
    val color =
        when {
            row.isTaken -> MaterialTheme.colorScheme.secondary
            row.isSkipped -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
    Box(
        modifier = Modifier.size(118.dp).background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(82.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (row.isTaken) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Text(
                    if (row.isSkipped) "–" else "Rx",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailsCard(
    row: DoseRow,
    medicine: Medicine?,
    patientMode: Boolean,
) {
    val context = LocalContext.current
    AppleCard {
        AppleSectionHeader(title = if (patientMode) "Notes" else "Details")
        if (!patientMode) {
            ShowcaseDetailLine("Time of day", row.slot.defaultLabel)
            row.checkedAt?.let {
                ShowcaseDetailLine(
                    "Taken at",
                    TimeFormatting.compact(context, it, row.checkedTimezone),
                )
            }
            row.skippedAt?.let {
                ShowcaseDetailLine("Not taken at", TimeFormatting.compact(context, it))
            }
            row.countdownMinutes?.let {
                ShowcaseDetailLine("Wait timer", CountdownLogic.formatDuration(it))
            }
        }
        medicine?.notes?.takeIf(String::isNotBlank)?.let {
            ShowcaseDetailLine("Notes", it)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailLine(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
