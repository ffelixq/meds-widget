package io.github.ffelixq.medswidget.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.domain.LogicalDayCalculator
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal fun interface ResetBoundaryWorkEnqueuer {
    fun enqueue(initialDelayMillis: Long)
}

private class WorkManagerResetBoundaryWorkEnqueuer(
    context: Context,
) : ResetBoundaryWorkEnqueuer {
    private val context = context.applicationContext

    override fun enqueue(initialDelayMillis: Long) {
        val request =
            OneTimeWorkRequestBuilder<ResetBoundaryWorker>()
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .addTag(ResetBoundaryScheduler.RESET_WORK_NAME)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ResetBoundaryScheduler.RESET_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

class ResetBoundaryScheduler internal constructor(
    private val clock: Clock,
    private val workEnqueuer: ResetBoundaryWorkEnqueuer,
) {
    constructor(context: Context, clock: Clock) :
        this(clock, WorkManagerResetBoundaryWorkEnqueuer(context.applicationContext))

    fun schedule(resetMinutesAfterMidnight: Int) {
        val delay =
            LogicalDayCalculator
                .delayUntilNextBoundary(clock.instant(), ZoneId.systemDefault(), resetMinutesAfterMidnight)
                .toMillis()
                .coerceAtLeast(MINIMUM_DELAY_MILLIS)
        workEnqueuer.enqueue(delay)
    }

    companion object {
        const val RESET_WORK_NAME = "meds-widget-reset-boundary"
        private const val MINIMUM_DELAY_MILLIS = 1_000L
    }
}

class ResetBoundaryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        try {
            MedsApplication.graph(applicationContext).refreshTemporalState()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
}

class TemporalRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        try {
            MedsApplication.graph(applicationContext).refreshTemporalState()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
}

class SystemTimeChangeReceiver : BroadcastReceiver() {
    internal var enqueueTemporalRefresh: (Context) -> Unit = ::enqueueTemporalRefreshWork

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        enqueueTemporalRefresh(context)
    }

    internal companion object {
        const val TEMPORAL_WORK_NAME = "meds-widget-temporal-refresh"
        val SUPPORTED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}

private fun enqueueTemporalRefreshWork(context: Context) {
    val request = OneTimeWorkRequestBuilder<TemporalRefreshWorker>().build()
    WorkManager
        .getInstance(context)
        .enqueueUniqueWork(
            SystemTimeChangeReceiver.TEMPORAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
}
