package io.github.ffelixq.medswidget.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.R
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.ui.MainActivity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MedicineReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun sync(
        uid: String,
        medicines: List<Medicine>,
    ) {
        workManager.cancelAllWorkByTag(ownerTag(uid))
        val today = LocalDate.now(ZoneId.systemDefault())
        medicines
            .asSequence()
            .filterNot(Medicine::archived)
            .filter { it.endDate == null || !today.isAfter(it.endDate) }
            .forEach { medicine ->
                medicine.enabledSlots().forEach { slot ->
                    medicine.reminderMinutes(slot)?.let { minutes ->
                        schedule(uid, medicine, slot, minutes)
                    }
                }
            }
    }

    fun cancel(uid: String) {
        workManager.cancelAllWorkByTag(ownerTag(uid))
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(REMINDER_TAG)
    }

    private fun schedule(
        uid: String,
        medicine: Medicine,
        slot: DoseSlot,
        minutesAfterMidnight: Int,
    ) {
        val workName = workName(uid, medicine.id, slot)
        val initialDelay =
            delayUntilReminder(
                now = clock.instant(),
                zone = ZoneId.systemDefault(),
                minutesAfterMidnight = minutesAfterMidnight,
                startDate = medicine.startDate,
            ).toMillis().coerceAtLeast(MINIMUM_DELAY_MILLIS)
        val request =
            PeriodicWorkRequestBuilder<MedicineReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_UID to uid,
                        KEY_MEDICINE_ID to medicine.id,
                        KEY_SLOT to slot.wireValue,
                        KEY_END_DATE to medicine.endDate?.toString(),
                        KEY_WORK_NAME to workName,
                    ),
                ).addTag(REMINDER_TAG)
                .addTag(ownerTag(uid))
                .build()
        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        internal const val REMINDER_TAG = "meds-widget-reminders"
        internal const val CHANNEL_ID = "medicine-reminders"
        internal const val KEY_UID = "uid"
        internal const val KEY_MEDICINE_ID = "medicine_id"
        internal const val KEY_SLOT = "slot"
        internal const val KEY_END_DATE = "end_date"
        internal const val KEY_WORK_NAME = "work_name"
        private const val MINIMUM_DELAY_MILLIS = 1_000L

        internal fun ownerTag(uid: String): String = "meds-widget-reminders-$uid"

        internal fun workName(
            uid: String,
            medicineId: String,
            slot: DoseSlot,
        ): String = "meds-widget-reminder-$uid-$medicineId-${slot.wireValue}"

        internal fun delayUntilReminder(
            now: Instant,
            zone: ZoneId,
            minutesAfterMidnight: Int,
            startDate: LocalDate? = null,
        ): Duration {
            val localNow = now.atZone(zone)
            var targetDate = localNow.toLocalDate()
            if (startDate != null && targetDate.isBefore(startDate)) targetDate = startDate
            var target = targetDate.atStartOfDay(zone).plusMinutes(minutesAfterMidnight.toLong())
            if (!target.toInstant().isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target.toInstant())
        }
    }
}

class MedicineReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val request = reminderRequest() ?: return Result.success()
        if (request.isExpired()) {
            request.workName?.let {
                WorkManager.getInstance(applicationContext).cancelUniqueWork(it)
            }
        } else if (canDeliver(request.uid)) {
            ensureChannel(applicationContext)
            val content = notificationContent(request)
            showNotification(
                applicationContext,
                request.medicineId,
                request.slot,
                content,
            )
        }
        return Result.success()
    }

    private fun reminderRequest(): ReminderRequest? {
        val uid = inputData.getString(MedicineReminderScheduler.KEY_UID) ?: return null
        val medicineId =
            inputData.getString(MedicineReminderScheduler.KEY_MEDICINE_ID) ?: return null
        val slot =
            DoseSlot.fromWire(inputData.getString(MedicineReminderScheduler.KEY_SLOT).orEmpty())
                ?: return null
        val endDate =
            inputData.getString(MedicineReminderScheduler.KEY_END_DATE)?.let { raw ->
                runCatching { LocalDate.parse(raw) }.getOrNull()
            }
        return ReminderRequest(
            uid = uid,
            medicineId = medicineId,
            slot = slot,
            endDate = endDate,
            workName = inputData.getString(MedicineReminderScheduler.KEY_WORK_NAME),
        )
    }

    private fun ReminderRequest.isExpired(): Boolean = endDate != null && LocalDate.now().isAfter(endDate)

    private fun canDeliver(uid: String): Boolean {
        val graph = MedsApplication.graph(applicationContext)
        val signedIn =
            graph.currentAuthenticatedUid == uid && !graph.accountOperationGate.isDeletionInProgress
        return signedIn && notificationsAllowed(applicationContext)
    }

    private suspend fun notificationContent(request: ReminderRequest): ReminderContent {
        val graph = MedsApplication.graph(applicationContext)
        val snapshot = graph.snapshotStore.read()
        if (!snapshot.signedIn || snapshot.ownerUid != request.uid) {
            return ReminderContent.generic(request.slot)
        }
        val medicine = snapshot.medicine(request.medicineId)
        val row =
            snapshot.rows.firstOrNull {
                it.medicineId == request.medicineId && it.slot == request.slot
            }
        return ReminderContent(
            title = medicine?.displayName?.takeIf(String::isNotBlank) ?: "Medicine reminder",
            message = row?.label?.takeIf(String::isNotBlank) ?: request.slot.defaultLabel,
        )
    }

    private data class ReminderRequest(
        val uid: String,
        val medicineId: String,
        val slot: DoseSlot,
        val endDate: LocalDate?,
        val workName: String?,
    )

    private data class ReminderContent(
        val title: String,
        val message: String,
    ) {
        companion object {
            fun generic(slot: DoseSlot): ReminderContent =
                ReminderContent(
                    title = "Medicine reminder",
                    message = slot.defaultLabel,
                )
        }
    }

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MedicineReminderScheduler.CHANNEL_ID,
                "Medicine reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Scheduled reminders for medicines you configure in Meds Widget."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun showNotification(
        context: Context,
        medicineId: String,
        slot: DoseSlot,
        content: ReminderContent,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openApp =
            PendingIntent.getActivity(
                context,
                (medicineId + slot.wireValue).hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val publicNotification =
            NotificationCompat
                .Builder(context, MedicineReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_meds)
                .setContentTitle("Medicine reminder")
                .setContentText("Open Meds Widget to view details.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
        val notification =
            NotificationCompat
                .Builder(context, MedicineReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_meds)
                .setContentTitle(content.title)
                .setContentText("Time for ${content.message}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(
                (medicineId + slot.wireValue).hashCode(),
                notification,
            )
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and delivery.
        }
    }
}
