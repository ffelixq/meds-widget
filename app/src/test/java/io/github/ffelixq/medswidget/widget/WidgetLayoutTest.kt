package io.github.ffelixq.medswidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun `single widget category considers width and height`() {
        assertEquals(
            WidgetLayoutCategory.COMPACT,
            WidgetLayoutSpec.forSize(DpSize(140.dp, 200.dp), WidgetKind.SINGLE).category,
        )
        assertEquals(
            WidgetLayoutCategory.COMPACT,
            WidgetLayoutSpec.forSize(DpSize(250.dp, 100.dp), WidgetKind.SINGLE).category,
        )
        assertEquals(
            WidgetLayoutCategory.STANDARD,
            WidgetLayoutSpec.forSize(DpSize(190.dp, 145.dp), WidgetKind.SINGLE).category,
        )
        assertEquals(
            WidgetLayoutCategory.SPACIOUS,
            WidgetLayoutSpec.forSize(DpSize(260.dp, 210.dp), WidgetKind.SINGLE).category,
        )
    }

    @Test
    fun `all widget scales from compact through resized spacious bounds`() {
        val compact = WidgetLayoutSpec.forSize(DpSize(250.dp, 110.dp), WidgetKind.ALL)
        val standard = WidgetLayoutSpec.forSize(DpSize(330.dp, 150.dp), WidgetKind.ALL)
        val spacious = WidgetLayoutSpec.forSize(DpSize(500.dp, 220.dp), WidgetKind.ALL)
        assertEquals(WidgetLayoutCategory.COMPACT, compact.category)
        assertEquals(WidgetLayoutCategory.STANDARD, standard.category)
        assertEquals(WidgetLayoutCategory.SPACIOUS, spacious.category)
        assertTrue(compact.titleSp < standard.titleSp)
        assertTrue(standard.titleSp < spacious.titleSp)
        assertTrue(compact.rowHeightDp < spacious.rowHeightDp)
    }
}
