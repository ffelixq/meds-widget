package io.github.ffelixq.medswidget.util

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

object SensitiveExportCleanup {
    private const val EXPORT_DIRECTORY = "exports"
    private const val FILE_PREFIX = "meds-widget-"
    private const val FILE_SUFFIX = ".csv"
    private const val CLEANUP_DELAY_MINUTES = 15L
    internal const val STALE_AFTER_MILLIS = 60L * 60L * 1_000L
    internal const val KEY_FILE_NAME = "export_file_name"

    fun createExportFile(
        context: Context,
        date: LocalDate,
    ): File {
        cleanupStale(context)
        return File(
            exportDirectory(context),
            "$FILE_PREFIX$date-${UUID.randomUUID()}$FILE_SUFFIX",
        )
    }

    fun scheduleDeletion(
        context: Context,
        file: File,
    ) {
        if (!isManagedExport(context, file)) return
        val request =
            OneTimeWorkRequestBuilder<SensitiveExportCleanupWorker>()
                .setInitialDelay(CLEANUP_DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_FILE_NAME to file.name))
                .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun cleanupStale(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        exportDirectory(context).listFiles()?.forEach { file ->
            val age = nowMillis - file.lastModified()
            if (file.isFile && isManagedExportName(file.name) && age >= STALE_AFTER_MILLIS) {
                runCatching { file.delete() }
            }
        }
    }

    internal fun deleteManagedExport(
        context: Context,
        fileName: String,
    ) {
        if (!isManagedExportName(fileName)) return
        val directory = exportDirectory(context)
        val candidate = File(directory, fileName)
        if (!isManagedExport(context, candidate)) return
        runCatching { candidate.delete() }
    }

    internal fun isManagedExportName(fileName: String): Boolean =
        fileName.startsWith(FILE_PREFIX) &&
            fileName.endsWith(FILE_SUFFIX) &&
            '/' !in fileName &&
            '\\' !in fileName

    private fun isManagedExport(
        context: Context,
        file: File,
    ): Boolean {
        if (!isManagedExportName(file.name)) return false
        val directory = exportDirectory(context)
        return runCatching {
            file.canonicalFile.parentFile == directory.canonicalFile
        }.getOrDefault(false)
    }

    private fun exportDirectory(context: Context): File = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
}

class SensitiveExportCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val fileName =
            inputData.getString(SensitiveExportCleanup.KEY_FILE_NAME)
                ?: return Result.success()
        SensitiveExportCleanup.deleteManagedExport(applicationContext, fileName)
        return Result.success()
    }
}
