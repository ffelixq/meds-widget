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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun PatientTodayScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onTake: (DoseRow) -> Unit,
    onOpenDose: (DoseRow) -> Unit,
    onRemindLater: (DoseRow) -> Unit,
    onStartCountdown: (DoseRow) -> Unit,
) {
    val pendingRows = state.rows.filter { !it.isTaken && !it.isSkipped }
    val nextDose = pendingRows.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "Today",
                subtitle = "${state.progress.completed} of ${state.progress.total} medicines taken",
            )
        }
        when {
            state.isLoading -> item { PatientMessageCard("Loading your medicines…") }
            state.medicines.isEmpty() -> item { PatientMessageCard("No medicines have been set up yet.") }
            nextDose != null -> {
                item {
                    PatientNextMedicineCard(
                        row = nextDose,
                        medicine = state.medicines.firstOrNull { it.id == nextDose.medicineId },
                        onTake = { onTake(nextDose) },
                        onOpenDose = { onOpenDose(nextDose) },
                        onRemindLater = { onRemindLater(nextDose) },
                        onStartCountdown = { onStartCountdown(nextDose) },
                    )
                }
            }
            state.rows.isNotEmpty() -> item { PatientMessageCard("✓ All medicines are recorded for today.") }
        }
        if (state.rows.isNotEmpty()) {
            item {
                AppleSectionHeader(
                    title = "Today’s medicines",
                    supportingText = "Tap a medicine to see more details.",
                )
            }
            item {
                AppleCard {
                    state.rows.forEach { row ->
                        PatientDoseRow(
                            row = row,
                            medicine = state.medicines.firstOrNull { it.id == row.medicineId },
                            onClick = { onOpenDose(row) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun PatientNextMedicineCard(
    row: DoseRow,
    medicine: Medicine?,
    onTake: () -> Unit,
    onOpenDose: () -> Unit,
    onRemindLater: () -> Unit,
    onStartCountdown: () -> Unit,
) {
    val countdown = showcaseCountdown(row)
    val status = patientStatusText(row, medicine)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Next medicine. ${row.medicineName}. ${row.label}. $status"
                },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "NEXT MEDICINE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                row.medicineName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            patientDoseAmount(medicine)?.let {
                Text(it, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                row.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                status,
                style = MaterialTheme.typography.titleLarge,
                color = patientStatusColor(row),
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onTake,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .semantics {
                            role = Role.Button
                            stateDescription = "Not yet recorded as taken"
                        },
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
            if (countdown.status == CountdownDisplayStatus.NOT_STARTED) {
                OutlinedButton(
                    onClick = onStartCountdown,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(countdown.text ?: "Start wait timer")
                }
            }
            OutlinedButton(
                onClick = onOpenDose,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Medicine details")
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PatientDoseRow(
    row: DoseRow,
    medicine: Medicine?,
    onClick: () -> Unit,
) {
    val status = patientStatusText(row, medicine)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .semantics {
                    role = Role.Button
                    stateDescription = status
                    contentDescription = "${row.medicineName}, ${row.label}, $status"
                }.clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                row.isTaken -> "✓"
                row.isSkipped -> "–"
                else -> "○"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = patientStatusColor(row),
            fontWeight = FontWeight.Bold,
        )
        Column(Modifier.weight(1f)) {
            Text(
                row.medicineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = patientStatusColor(row),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PatientMessageCard(message: String) {
    AppleCard {
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun patientStatusColor(row: DoseRow) =
    when {
        row.isTaken -> MaterialTheme.colorScheme.secondary
        row.isSkipped -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

private fun patientStatusText(
    row: DoseRow,
    medicine: Medicine?,
): String {
    if (row.isTaken) return "Taken"
    if (row.isSkipped) return "Not taking this dose"
    val countdown = showcaseCountdown(row)
    if (countdown.status == CountdownDisplayStatus.READY) return "You can take it now"
    if (countdown.status == CountdownDisplayStatus.RUNNING) return "Wait ${countdown.text.orEmpty()}"
    val reminderMinutes = medicine?.reminderMinutes(row.slot)
    if (reminderMinutes != null) {
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val reminderTime = LocalTime.of(reminderMinutes / 60, reminderMinutes % 60)
        return if (nowMinutes >= reminderMinutes) {
            "Not recorded yet"
        } else {
            "Later at ${reminderTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
        }
    }
    return "Not recorded yet"
}

private fun patientDoseAmount(medicine: Medicine?): String? {
    medicine ?: return null
    if (!medicine.supplyEnabled && medicine.unitsPerDose == 1.0 && medicine.supplyUnitName == "units") {
        return null
    }
    val amount =
        if (medicine.unitsPerDose % 1.0 == 0.0) {
            medicine.unitsPerDose.toInt().toString()
        } else {
            medicine.unitsPerDose.toString()
        }
    return "$amount ${medicine.supplyUnitName}"
}
