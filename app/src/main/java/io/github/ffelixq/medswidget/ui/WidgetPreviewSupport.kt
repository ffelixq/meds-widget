package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.widget.WidgetKind
import io.github.ffelixq.medswidget.widget.WidgetLayoutSpec
import java.time.Instant

@Suppress("FunctionNaming")
@Composable
internal fun WidgetPreviews(
    state: MainUiState,
    onCheck: (DoseRow) -> Unit,
    onStartCountdown: (DoseRow) -> Unit,
) {
    val firstMedicine =
        state.medicines.firstOrNull { medicine ->
            state.rows.any { it.medicineId == medicine.id }
        } ?: return
    val rows = state.rows.filter { it.medicineId == firstMedicine.id }
    val singleSpec = WidgetLayoutSpec.forSize(DpSize(190.dp, 145.dp), WidgetKind.SINGLE)
    val allSpec = WidgetLayoutSpec.forSize(DpSize(320.dp, 160.dp), WidgetKind.ALL)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Widget previews", style = MaterialTheme.typography.titleLarge)
        Text(
            "Live previews use the same dose actions as the real widgets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppleCard {
            Text(
                "2×2 · ${firstMedicine.widgetDisplayName()}",
                fontSize = singleSpec.titleSp.sp,
                fontWeight = FontWeight.SemiBold,
            )
            rows.forEach { row ->
                PreviewRow(
                    row = row,
                    spec = singleSpec,
                    onCheck = { onCheck(row) },
                    onStartCountdown = { onStartCountdown(row) },
                )
            }
        }
        AppleCard {
            Text(
                "4×2 · ${state.progress.compactDisplay}",
                fontSize = allSpec.titleSp.sp,
                fontWeight = FontWeight.SemiBold,
            )
            state.rows.take(4).forEach { row ->
                val displayName =
                    state.medicines
                        .firstOrNull { it.id == row.medicineId }
                        ?.widgetDisplayName()
                        ?: "Medicine"
                Text(
                    displayName,
                    fontSize = allSpec.supportingSp.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PreviewRow(
                    row = row,
                    spec = allSpec,
                    onCheck = { onCheck(row) },
                    onStartCountdown = { onStartCountdown(row) },
                )
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
    val visual = previewVisual(row)
    Row(
        modifier = Modifier.fillMaxWidth().height(spec.rowHeightDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier =
                Modifier
                    .size((spec.checkSp + 10).dp)
                    .clickable(
                        enabled = !row.isTaken && !row.isSkipped,
                        onClick = onCheck,
                    ),
            shape = CircleShape,
            color = visual.containerColor(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    visual.mark,
                    fontSize = spec.bodySp.sp,
                    color = visual.contentColor(),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(row.label, fontSize = spec.bodySp.sp, modifier = Modifier.weight(1f))
        when {
            row.isTaken || row.isSkipped -> {
                Unit
            }

            countdown.status == CountdownDisplayStatus.NOT_STARTED -> {
                TextButton(onClick = onStartCountdown) {
                    Text(countdown.text.orEmpty())
                }
            }

            else -> {
                Text(countdown.text.orEmpty(), fontSize = spec.supportingSp.sp)
            }
        }
    }
}

private data class PreviewVisual(
    val mark: String,
    val container: PreviewTone,
    val content: PreviewTone,
) {
    @Composable
    fun containerColor(): Color = container.color()

    @Composable
    fun contentColor(): Color = content.color()
}

private enum class PreviewTone {
    SECONDARY,
    ON_SECONDARY,
    TERTIARY,
    ON_TERTIARY,
    SURFACE_VARIANT,
    ON_SURFACE_VARIANT,
    ;

    @Composable
    fun color(): Color =
        when (this) {
            SECONDARY -> {
                MaterialTheme.colorScheme.secondary
            }

            ON_SECONDARY -> {
                MaterialTheme.colorScheme.onSecondary
            }

            TERTIARY -> {
                MaterialTheme.colorScheme.tertiary
            }

            ON_TERTIARY -> {
                MaterialTheme.colorScheme.onTertiary
            }

            SURFACE_VARIANT -> {
                MaterialTheme.colorScheme.surfaceVariant
            }

            ON_SURFACE_VARIANT -> {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
}

private fun previewVisual(row: DoseRow): PreviewVisual =
    when {
        row.isTaken -> {
            PreviewVisual("✓", PreviewTone.SECONDARY, PreviewTone.ON_SECONDARY)
        }

        row.isSkipped -> {
            PreviewVisual("–", PreviewTone.TERTIARY, PreviewTone.ON_TERTIARY)
        }

        else -> {
            PreviewVisual("", PreviewTone.SURFACE_VARIANT, PreviewTone.ON_SURFACE_VARIANT)
        }
    }
