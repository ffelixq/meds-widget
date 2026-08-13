package io.github.ffelixq.medswidget.widget

import androidx.compose.ui.unit.DpSize

internal data class AutomaticWidgetRows(
    val visible: List<WidgetDoseRow>,
    val hiddenCount: Int,
    val rowHeightDp: Int,
)

internal fun automaticWidgetRows(
    rows: List<WidgetDoseRow>,
    availableSize: DpSize,
    spec: WidgetLayoutSpec,
): AutomaticWidgetRows {
    if (rows.isEmpty()) {
        return AutomaticWidgetRows(emptyList(), 0, spec.rowHeightDp)
    }
    val maximumRows =
        when {
            availableSize.height.value < 180f -> 3
            availableSize.height.value < 300f -> 5
            else -> 7
        }
    val hasOverflow = rows.size > maximumRows
    val visibleLimit =
        if (hasOverflow) {
            (maximumRows - 1).coerceAtLeast(1)
        } else {
            maximumRows
        }
    val visible = rows.take(visibleLimit)
    val hiddenCount = (rows.size - visible.size).coerceAtLeast(0)
    val overflowBudget = if (hiddenCount > 0) 28 else 0
    val fixedBudget = spec.outerPaddingDp * 2 + spec.titleSp + 10 + overflowBudget
    val availableForRows = (availableSize.height.value.toInt() - fixedBudget).coerceAtLeast(visible.size * 30)
    val rowHeight =
        (availableForRows / visible.size)
            .coerceIn(30, spec.rowHeightDp)
    return AutomaticWidgetRows(visible, hiddenCount, rowHeight)
}
