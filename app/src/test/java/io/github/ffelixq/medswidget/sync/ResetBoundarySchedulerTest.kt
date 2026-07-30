package io.github.ffelixq.medswidget.sync

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ResetBoundarySchedulerTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun setUpTimeZone() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun `scheduler calculates the boundary delay before enqueuing work`() {
        var capturedDelay: Long? = null
        val clock = Clock.fixed(Instant.parse("2026-07-29T17:30:00Z"), zone)
        val scheduler =
            ResetBoundaryScheduler(
                clock,
                ResetBoundaryWorkEnqueuer { capturedDelay = it },
            )

        scheduler.schedule(resetMinutesAfterMidnight = 120)

        assertEquals(TimeUnit.MINUTES.toMillis(30), capturedDelay)
    }

    @Test
    fun `scheduler enqueues tagged unique reset work through WorkManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)
        val clock = Clock.fixed(Instant.parse("2026-07-29T17:30:00Z"), zone)

        ResetBoundaryScheduler(context, clock).schedule(resetMinutesAfterMidnight = 0)

        val work =
            workManager
                .getWorkInfosForUniqueWork(ResetBoundaryScheduler.RESET_WORK_NAME)
                .get(5, TimeUnit.SECONDS)
                .single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertTrue(ResetBoundaryScheduler.RESET_WORK_NAME in work.tags)
    }

    @Test
    fun `supported system time broadcasts request one temporal refresh each`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receivedContexts = mutableListOf<Context>()
        val receiver =
            SystemTimeChangeReceiver().apply {
                enqueueTemporalRefresh = { receivedContexts += it }
            }

        SystemTimeChangeReceiver.SUPPORTED_ACTIONS.forEach { action ->
            receiver.onReceive(context, Intent(action))
        }

        assertEquals(SystemTimeChangeReceiver.SUPPORTED_ACTIONS.size, receivedContexts.size)
        receivedContexts.forEach { assertSame(context, it) }
    }

    @Test
    fun `unrelated and missing broadcast actions are ignored`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var enqueueCount = 0
        val receiver =
            SystemTimeChangeReceiver().apply {
                enqueueTemporalRefresh = { enqueueCount += 1 }
            }

        receiver.onReceive(context, Intent("io.github.ffelixq.medswidget.UNRELATED"))
        receiver.onReceive(context, Intent())

        assertEquals(0, enqueueCount)
    }
}
