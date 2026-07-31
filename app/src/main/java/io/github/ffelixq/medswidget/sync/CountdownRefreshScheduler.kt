package io.github.ffelixq.medswidget.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.CountdownStatus
import io.github.ffelixq.medswidget.widget.WidgetSnapshot
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

internal fun interface CountdownRefreshWorkEnqueuer {
    fun enqueue(initialDelayMillis: Long)
}

private class WorkManagerCountdownRefreshWorkEnqueuer(
    context: Context,
) : CountdownRefreshWorkEnqueuer {
    private val context = context.applicationContext

    override fun enqueue(initialDelayMillis: Long) {
        val request =
            OneTimeWorkRequestBuilder<CountdownRefreshWorker>()
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .addTag(CountdownRefreshScheduler.WORK_NAME)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CountdownRefreshScheduler.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

class CountdownRefreshScheduler internal constructor(
    private val clock: Clock,
    private val enqueuer: CountdownRefreshWorkEnqueuer,
) {
    constructor(context: Context, clock: Clock) :
        this(clock, WorkManagerCountdownRefreshWorkEnqueuer(context.applicationContext))

    fun schedule(snapshot: WidgetSnapshot) {
        val now = clock.instant()
        val delay =
            snapshot.rows
                .mapNotNull { it.countdown }
                .filter { it.status == CountdownStatus.RUNNING && now.isBefore(it.targetAt) }
                .map { CountdownLogic.nextRefreshDelay(now, it.targetAt) }
                .minOrNull()
                ?: return
        enqueuer.enqueue(delay.coerceAtLeast(Duration.ofSeconds(1)).toMillis())
    }

    companion object {
        const val WORK_NAME = "meds-widget-countdown-refresh"
    }
}

class CountdownRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        try {
            val graph = MedsApplication.graph(applicationContext)
            graph.refreshCountdownDisplay()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
}
