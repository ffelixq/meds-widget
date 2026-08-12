package io.github.ffelixq.medswidget.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.ffelixq.medswidget.domain.DoseSlot
import java.util.concurrent.TimeUnit

class SnoozeReminderScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        delayMinutes: Int,
    ) {
        val minutes = delayMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val request =
            OneTimeWorkRequestBuilder<MedicineReminderWorker>()
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        MedicineReminderScheduler.KEY_UID to uid,
                        MedicineReminderScheduler.KEY_MEDICINE_ID to medicineId,
                        MedicineReminderScheduler.KEY_SLOT to slot.wireValue,
                    ),
                ).addTag(MedicineReminderScheduler.REMINDER_TAG)
                .addTag(MedicineReminderScheduler.ownerTag(uid))
                .build()
        workManager.enqueueUniqueWork(
            workName(uid, medicineId, slot),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        internal const val MIN_MINUTES = 5
        internal const val MAX_MINUTES = 24 * 60

        internal fun workName(
            uid: String,
            medicineId: String,
            slot: DoseSlot,
        ): String = "meds-widget-remind-later-$uid-$medicineId-${slot.wireValue}"
    }
}
