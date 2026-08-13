package io.github.ffelixq.medswidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.DoseSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticWidgetRowsTest {
    @Test
    fun `compact automatic widget reserves an overflow row`() {
        val size = DpSize(320.dp, 150.dp)
        val spec = WidgetLayoutSpec.forSize(size, WidgetKind.ALL)
        val result = automaticWidgetRows(rows(12), size, spec)

        assertEquals(2, result.visible.size)
        assertEquals(10, result.hiddenCount)
        assertTrue(result.rowHeightDp >= 30)
    }

    @Test
    fun `dashboard-sized automatic widget shows more rows without a collection`() {
        val size = DpSize(320.dp, 320.dp)
        val spec = WidgetLayoutSpec.forSize(size, WidgetKind.ALL)
        val result = automaticWidgetRows(rows(8), size, spec)

        assertEquals(6, result.visible.size)
        assertEquals(2, result.hiddenCount)
    }

    private fun rows(count: Int): List<WidgetDoseRow> =
        (1..count).map { index ->
            WidgetDoseRow(
                medicineId = "medicine-$index",
                medicineName = "Medicine $index",
                slot = DoseSlot.AFTERNOON,
                label = "Dose $index",
                isTaken = false,
                checkedAt = null,
            )
        }
}
