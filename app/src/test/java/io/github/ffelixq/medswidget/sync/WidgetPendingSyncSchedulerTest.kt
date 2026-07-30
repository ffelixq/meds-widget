package io.github.ffelixq.medswidget.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPendingSyncSchedulerTest {
    @Test
    fun `every optimistic action requests unique pending reconciliation work`() {
        var enqueues = 0
        val scheduler = WidgetPendingSyncScheduler { enqueues += 1 }

        scheduler.schedule()
        scheduler.schedule()

        assertEquals(2, enqueues)
        assertEquals("meds-widget-pending-sync", WidgetPendingSyncScheduler.WORK_NAME)
    }
}
