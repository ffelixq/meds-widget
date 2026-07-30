package io.github.ffelixq.medswidget.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ffelixq.medswidget.MedsApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

internal fun interface WidgetPendingSyncEnqueuer {
    fun enqueue()
}

private class WorkManagerWidgetPendingSyncEnqueuer(
    context: Context,
) : WidgetPendingSyncEnqueuer {
    private val context = context.applicationContext

    override fun enqueue() {
        val request =
            OneTimeWorkRequestBuilder<WidgetPendingSyncWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).setInitialDelay(RECONCILIATION_DELAY_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MINIMUM_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                ).addTag(WidgetPendingSyncScheduler.WORK_NAME)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WidgetPendingSyncScheduler.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val RECONCILIATION_DELAY_SECONDS = 2L
        const val MINIMUM_BACKOFF_SECONDS = 10L
    }
}

class WidgetPendingSyncScheduler internal constructor(
    private val enqueuer: WidgetPendingSyncEnqueuer,
) {
    constructor(context: Context) :
        this(WorkManagerWidgetPendingSyncEnqueuer(context.applicationContext))

    fun schedule() {
        enqueuer.enqueue()
    }

    companion object {
        const val WORK_NAME = "meds-widget-pending-sync"
    }
}

class WidgetPendingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        try {
            if (MedsApplication.graph(applicationContext).reconcilePendingWidgetActions()) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
}
