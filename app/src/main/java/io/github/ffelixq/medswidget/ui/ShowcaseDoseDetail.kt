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
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onUndo: () -> Unit,
    onSkip: () -> Unit,
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        }
        item { ShowcaseDetailHeader(row, countdown) }
        item { ShowcaseDetailPrimaryActions(row, onCheck, onUndo, onSkip) }
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
        item { ShowcaseDetailsCard(row, medicine) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailHeader(
    row: DoseRow,
    countdown: CountdownDisplay,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShowcaseDetailStatusCircle(row)
        Text(
            row.label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            row.medicineName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val status =
            when {
                row.isTaken -> "Taken"
                row.isSkipped -> "Skipped"
                countdown.status == CountdownDisplayStatus.READY -> "Ready"
                countdown.status == CountdownDisplayStatus.RUNNING -> countdown.text.orEmpty()
                else -> "Pending"
            }
        Text(
            status,
            style = MaterialTheme.typography.titleMedium,
            color =
                when {
                    row.isTaken -> MaterialTheme.colorScheme.secondary
                    row.isSkipped -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailPrimaryActions(
    row: DoseRow,
    onCheck: () -> Unit,
    onUndo: () -> Unit,
    onSkip: () -> Unit,
) {
    if (row.isTaken || row.isSkipped) {
        OutlinedButton(
            onClick = onUndo,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Undo")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Take now")
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip dose")
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
            supportingText =
                "Configured for ${CountdownLogic.formatDuration(row.countdownMinutes ?: 1)}.",
        )
        if (countdown.status == CountdownDisplayStatus.NOT_STARTED) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(countdown.text ?: "Start timer")
            }
        } else if (
            countdown.status == CountdownDisplayStatus.RUNNING ||
            countdown.status == CountdownDisplayStatus.READY
        ) {
            Text(
                countdown.text.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) {
                    Text("Restart")
                }
            }
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
) {
    val context = LocalContext.current
    AppleCard {
        AppleSectionHeader(title = "Details")
        ShowcaseDetailLine("Slot", row.slot.defaultLabel)
        row.checkedAt?.let {
            ShowcaseDetailLine(
                "Taken at",
                TimeFormatting.compact(context, it, row.checkedTimezone),
            )
        }
        row.skippedAt?.let {
            ShowcaseDetailLine("Skipped at", TimeFormatting.compact(context, it))
        }
        row.countdownMinutes?.let {
            ShowcaseDetailLine("Wait timer", CountdownLogic.formatDuration(it))
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f),
        )
    }
}
