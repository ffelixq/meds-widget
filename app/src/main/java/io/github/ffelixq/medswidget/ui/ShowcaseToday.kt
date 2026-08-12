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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownDisplay
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill
import java.time.format.DateTimeFormatter

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun ShowcaseTodayScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onCheck: (DoseRow, CheckSource) -> Unit,
    onStartCountdown: (DoseRow, CheckSource) -> Unit,
    onOpenDose: (DoseRow) -> Unit,
    onAdd: () -> Unit,
    onRemindLater: (DoseRow) -> Unit,
) {
    val nextDose = state.rows.firstOrNull { !it.isTaken && !it.isSkipped }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "Today",
                subtitle = state.logicalDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
            )
        }
        item { ShowcaseProgressCard(state) }
        item {
            when {
                state.isLoading -> {
                    ShowcaseLoadingCard("Loading your medicines…")
                }

                state.medicines.isEmpty() -> {
                    ShowcaseEmptyToday(onAdd)
                }

                nextDose != null -> {
                    ShowcaseHeroDoseCard(
                        row = nextDose,
                        onTake = { onCheck(nextDose, CheckSource.APP) },
                        onStartCountdown = { onStartCountdown(nextDose, CheckSource.APP) },
                        onRemindLater = { onRemindLater(nextDose) },
                        onOpenDose = { onOpenDose(nextDose) },
                    )
                }

                state.rows.isNotEmpty() -> {
                    ShowcaseAllDoneCard()
                }
            }
        }
        if (state.rows.isNotEmpty()) {
            item {
                AppleSectionHeader(
                    title = "Today’s medicines",
                    supportingText =
                        "Tap a medicine for details, notes, timer controls, or corrections.",
                )
            }
            item {
                AppleCard {
                    state.rows.forEach { row ->
                        ShowcaseDoseListRow(
                            row = row,
                            onOpen = { onOpenDose(row) },
                            onTake = { onCheck(row, CheckSource.APP) },
                            onStartCountdown = { onStartCountdown(row, CheckSource.APP) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseProgressCard(state: MainUiState) {
    AppleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${state.progress.completed} of ${state.progress.total}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "medicines taken",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AppleStatusPill(
                text = "${state.progress.pending} not recorded",
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.progress.total > 0) {
            LinearProgressIndicator(
                progress = { state.progress.completed.toFloat() / state.progress.total },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        if (state.progress.skipped > 0) {
            Text(
                "${state.progress.skipped} marked not taken today",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            state.hasPendingWrites -> {
                Text("Syncing changes…", color = MaterialTheme.colorScheme.primary)
            }

            state.isCached -> {
                Text(
                    "Showing saved offline data",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ShowcaseHeroDoseCard(
    row: DoseRow,
    onTake: () -> Unit,
    onStartCountdown: () -> Unit,
    onRemindLater: () -> Unit,
    onOpenDose: () -> Unit,
) {
    val countdown = showcaseCountdown(row)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Next medicine, ${row.medicineName}, ${row.label}"
                },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShowcaseHeroHeader(row, onOpenDose)
            ShowcaseHeroCountdown(row, countdown)
            Button(
                onClick = onTake,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .semantics {
                            role = Role.Button
                            stateDescription = "Not yet recorded as taken"
                        },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("✓  I took it", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onRemindLater,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Remind me later")
            }
            if (countdown.status == CountdownDisplayStatus.NOT_STARTED) {
                OutlinedButton(
                    onClick = onStartCountdown,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(countdown.text ?: "Start wait timer")
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseHeroHeader(
    row: DoseRow,
    onOpenDose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.medicineName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenDose) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Medicine details",
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseHeroCountdown(
    row: DoseRow,
    countdown: CountdownDisplay,
) {
    when (countdown.status) {
        CountdownDisplayStatus.RUNNING -> {
            Text(
                "Wait ${countdown.text.orEmpty()}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        CountdownDisplayStatus.READY -> {
            Text(
                "You can take it now",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
        }

        CountdownDisplayStatus.NOT_STARTED -> {
            Text(
                "Wait timer: ${CountdownLogic.formatDuration(row.countdownMinutes ?: 1)}",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        else -> {
            Text("Not recorded yet", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDoseListRow(
    row: DoseRow,
    onOpen: () -> Unit,
    onTake: () -> Unit,
    onStartCountdown: () -> Unit,
) {
    val countdown = showcaseCountdown(row)
    val status =
        when {
            row.isTaken -> "Taken"
            row.isSkipped -> "Not taken"
            countdown.status == CountdownDisplayStatus.READY -> "You can take it now"
            countdown.status == CountdownDisplayStatus.RUNNING -> "Wait ${countdown.text.orEmpty()}"
            else -> "Not recorded"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .semantics {
                    role = Role.Button
                    stateDescription = status
                    contentDescription = "${row.medicineName}, ${row.label}, $status"
                }.clickable(onClick = onOpen)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShowcaseStatusDot(row)
        Column(Modifier.weight(1f)) {
            Text(
                row.medicineName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        when {
            row.isTaken || row.isSkipped -> {
                Unit
            }

            countdown.status == CountdownDisplayStatus.NOT_STARTED -> {
                TextButton(onClick = onStartCountdown) { Text("Start timer") }
            }

            countdown.status != CountdownDisplayStatus.RUNNING &&
                countdown.status != CountdownDisplayStatus.READY -> {
                TextButton(onClick = onTake) { Text("I took it") }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseStatusDot(row: DoseRow) {
    val background =
        when {
            row.isTaken -> MaterialTheme.colorScheme.secondary
            row.isSkipped -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    Box(
        modifier = Modifier.size(42.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            row.isTaken -> {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White)
            }

            row.isSkipped -> {
                Text(
                    "–",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold,
                )
            }

            else -> {
                Text("○", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseLoadingCard(message: String) {
    AppleCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseEmptyToday(onAdd: () -> Unit) {
    AppleCard {
        Text("No medicines yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Add your first medicine to build your Today plan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("  Add medicine")
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseAllDoneCard() {
    AppleCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White)
            }
            Column {
                Text("Today is complete", style = MaterialTheme.typography.titleLarge)
                Text(
                    "All scheduled medicines have been recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
