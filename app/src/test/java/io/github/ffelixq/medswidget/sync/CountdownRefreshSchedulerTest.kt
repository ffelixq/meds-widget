package io.github.ffelixq.medswidget.sync

import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.CountdownStatus
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.widget.WidgetDoseRow
import io.github.ffelixq.medswidget.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CountdownRefreshSchedulerTest {
    private val now = Instant.parse("2026-07-29T05:00:00Z")

    @Test
    fun `scheduler uses nearest adaptive timer boundary`() {
        val delays = mutableListOf<Long>()
        val scheduler =
            CountdownRefreshScheduler(
                Clock.fixed(now, ZoneOffset.UTC),
                CountdownRefreshWorkEnqueuer(delays::add),
            )
        scheduler.schedule(snapshot(now.plusSeconds(2 * 60 * 60), now.plusSeconds(8 * 60)))

        assertEquals(60_000L, delays.single())
    }

    @Test
    fun `snapshot without active countdown does not enqueue permanent polling`() {
        val delays = mutableListOf<Long>()
        val scheduler =
            CountdownRefreshScheduler(
                Clock.fixed(now, ZoneOffset.UTC),
                CountdownRefreshWorkEnqueuer(delays::add),
            )
        scheduler.schedule(WidgetSnapshot())

        assertTrue(delays.isEmpty())
    }

    @Test
    fun `ready countdown does not enqueue a one-second refresh loop`() {
        val delays = mutableListOf<Long>()
        val scheduler =
            CountdownRefreshScheduler(
                Clock.fixed(now, ZoneOffset.UTC),
                CountdownRefreshWorkEnqueuer(delays::add),
            )
        scheduler.schedule(snapshot(now))

        assertTrue(delays.isEmpty())
    }

    private fun snapshot(vararg targets: Instant): WidgetSnapshot =
        WidgetSnapshot(
            rows =
                targets.mapIndexed { index, target ->
                    WidgetDoseRow(
                        medicineId = "medicine-$index",
                        medicineName = "Medicine",
                        slot = DoseSlot.AFTERNOON,
                        label = "After lunch",
                        isTaken = false,
                        checkedAt = null,
                        countdownMinutes = 120,
                        countdown =
                            CountdownState(
                                id = "2026-07-29_medicine-$index-afternoon",
                                ownerUid = "user-a",
                                logicalDay = LocalDate.of(2026, 7, 29),
                                medicineId = "medicine-$index",
                                slot = DoseSlot.AFTERNOON,
                                durationMinutes = 120,
                                startedAt = now,
                                targetAt = target,
                                startedTimezone = "UTC",
                                startedSource = CheckSource.APP,
                                status = CountdownStatus.RUNNING,
                                cancelledAt = null,
                                completedAt = null,
                                lastActionId = "action-$index",
                            ),
                    )
                },
        )
}
