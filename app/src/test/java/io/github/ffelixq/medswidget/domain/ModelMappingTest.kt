package io.github.ffelixq.medswidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMappingTest {
    @Test
    fun `dose slot wire mappings accept only supported values`() {
        assertEquals(DoseSlot.AFTERNOON, DoseSlot.fromWire("afternoon"))
        assertEquals(DoseSlot.NIGHT, DoseSlot.fromWire("night"))
        assertNull(DoseSlot.fromWire("morning"))
        assertNull(DoseSlot.fromWire("NIGHT"))
    }

    @Test
    fun `check source wire mappings cover every supported source`() {
        CheckSource.entries.forEach { source ->
            assertSame(source, CheckSource.fromWire(source.wireValue))
        }
        assertNull(CheckSource.fromWire("widget"))
    }

    @Test
    fun `dose action wire mappings reject unknown actions`() {
        assertEquals(DoseAction.CHECK, DoseAction.fromWire("check"))
        assertEquals(DoseAction.UNDO, DoseAction.fromWire("undo"))
        assertNull(DoseAction.fromWire("delete"))
    }

    @Test
    fun `unknown theme preferences safely map to system`() {
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromWire("light"))
        assertEquals(ThemePreference.DARK, ThemePreference.fromWire("dark"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromWire("unexpected"))
    }

    @Test
    fun `signed-out content state is explicit and carries no account content`() {
        val state: ContentState<List<Medicine>> = ContentState.SignedOut

        assertTrue(state is ContentState.SignedOut)
    }

    @Test
    fun `content state records cached and pending sync flags independently`() {
        val content =
            ContentState.Content(
                value = listOf("cached"),
                isCached = true,
                isSyncPending = true,
            )

        assertTrue(content.isCached)
        assertTrue(content.isSyncPending)
        assertEquals(listOf("cached"), content.value)
    }
}
