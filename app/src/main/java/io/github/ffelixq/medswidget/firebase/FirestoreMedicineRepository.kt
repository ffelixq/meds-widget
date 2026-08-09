package io.github.ffelixq.medswidget.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import io.github.ffelixq.medswidget.data.MedicineRepository
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.MEDICINE_SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.sync.OutstandingWriteTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import java.util.UUID

class FirestoreMedicineRepository(
    private val firestore: FirebaseFirestore,
    private val outstandingWriteTracker: OutstandingWriteTracker = OutstandingWriteTracker(),
) : MedicineRepository {
    private val writeFailures = UidScopedWriteFailures(WRITE_FAILURE_MESSAGE)

    override fun observeActive(uid: String): Flow<DataEnvelope<List<Medicine>>> =
        observe(
            uid,
            FirestorePaths
                .medicines(firestore, uid)
                .whereEqualTo("archived", false)
                .orderBy("createdAt", Query.Direction.ASCENDING),
        )

    override fun observeAll(uid: String): Flow<DataEnvelope<List<Medicine>>> =
        observe(
            uid,
            FirestorePaths.medicines(firestore, uid).orderBy("createdAt", Query.Direction.ASCENDING),
        )

    override suspend fun save(
        uid: String,
        draft: MedicineDraft,
    ): String {
        val id = draft.id ?: UUID.randomUUID().toString()
        val reference = FirestorePaths.medicines(firestore, uid).document(id)
        val values =
            mutableMapOf<String, Any?>(
                "id" to id,
                "ownerUid" to uid,
                "name" to draft.name,
                "nickname" to draft.nickname,
                "notes" to draft.notes,
                "widgetNameMode" to draft.widgetNameMode.wireValue,
                "morningEnabled" to draft.morningEnabled,
                "morningLabel" to draft.morningLabel,
                "morningCountdownMinutes" to draft.morningCountdownMinutes,
                "morningReminderMinutes" to draft.morningReminderMinutes,
                "afternoonEnabled" to draft.afternoonEnabled,
                "afternoonLabel" to draft.afternoonLabel,
                "afternoonCountdownMinutes" to draft.afternoonCountdownMinutes,
                "afternoonReminderMinutes" to draft.afternoonReminderMinutes,
                "eveningEnabled" to draft.eveningEnabled,
                "eveningLabel" to draft.eveningLabel,
                "eveningCountdownMinutes" to draft.eveningCountdownMinutes,
                "eveningReminderMinutes" to draft.eveningReminderMinutes,
                "nightEnabled" to draft.nightEnabled,
                "nightLabel" to draft.nightLabel,
                "nightCountdownMinutes" to draft.nightCountdownMinutes,
                "nightReminderMinutes" to draft.nightReminderMinutes,
                "startDate" to draft.startDate?.toString(),
                "endDate" to draft.endDate?.toString(),
                "supplyEnabled" to draft.supplyEnabled,
                "supplyInitialUnits" to draft.supplyInitialUnits,
                "unitsPerDose" to draft.unitsPerDose,
                "lowSupplyThreshold" to draft.lowSupplyThreshold,
                "supplyUnitName" to draft.supplyUnitName,
                "archived" to false,
                "updatedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to MEDICINE_SCHEMA_VERSION,
            )
        if (draft.id == null) {
            values["createdAt"] = FieldValue.serverTimestamp()
            dispatchWrite(uid) { reference.set(values) }
        } else {
            dispatchWrite(uid) { reference.set(values, SetOptions.merge()) }
        }
        return id
    }

    override suspend fun archive(
        uid: String,
        medicineId: String,
        archived: Boolean,
    ) {
        dispatchWrite(uid) {
            FirestorePaths
                .medicines(firestore, uid)
                .document(medicineId)
                .update(mapOf("archived" to archived, "updatedAt" to FieldValue.serverTimestamp()))
        }
    }

    override suspend fun delete(
        uid: String,
        medicineId: String,
    ) {
        dispatchWrite(uid) {
            FirestorePaths
                .medicines(firestore, uid)
                .document(medicineId)
                .delete()
        }
    }

    override suspend fun refill(
        uid: String,
        medicineId: String,
        units: Double,
    ) {
        require(units.isFinite() && units > 0.0) { "Refill amount must be greater than 0." }
        dispatchWrite(uid) {
            FirestorePaths
                .medicines(firestore, uid)
                .document(medicineId)
                .update(
                    mapOf(
                        "supplyInitialUnits" to FieldValue.increment(units),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
        }
    }

    private fun observe(
        uid: String,
        query: Query,
    ): Flow<DataEnvelope<List<Medicine>>> =
        combine(observeSnapshots(uid, query), writeFailures.observe(uid)) { envelope, failure ->
            envelope.copy(errorMessage = failure ?: envelope.errorMessage)
        }

    private fun observeSnapshots(
        uid: String,
        query: Query,
    ): Flow<DataEnvelope<List<Medicine>>> =
        callbackFlow {
            var lastValue = emptyList<Medicine>()
            val registration =
                query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        trySend(
                            DataEnvelope(
                                value = lastValue,
                                fromCache = true,
                                errorMessage = "Medicine data could not be refreshed.",
                            ),
                        )
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        writeFailures.recordHealthySnapshot(uid)
                        lastValue = snapshot.documents.mapNotNull { it.toMedicine() }
                        trySend(
                            DataEnvelope(
                                value = lastValue,
                                fromCache = snapshot.metadata.isFromCache,
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                            ),
                        )
                    }
                }
            awaitClose { registration.remove() }
        }

    @Suppress("TooGenericExceptionCaught")
    private fun dispatchWrite(
        uid: String,
        taskFactory: () -> com.google.android.gms.tasks.Task<Void>,
    ) {
        val failureOperation = writeFailures.begin(uid)
        val outstandingTicket = outstandingWriteTracker.begin(uid)
        try {
            taskFactory().trackWrite(failureOperation, outstandingTicket)
        } catch (error: RuntimeException) {
            try {
                writeFailures.recordFailure(failureOperation)
            } finally {
                outstandingWriteTracker.complete(outstandingTicket)
            }
            throw error
        }
    }

    private fun com.google.android.gms.tasks.Task<Void>.trackWrite(
        failureOperation: UidScopedWriteFailures.Operation,
        outstandingTicket: OutstandingWriteTracker.Ticket,
    ) {
        addOnCompleteListener { completedTask ->
            try {
                if (completedTask.isSuccessful) {
                    writeFailures.recordSuccess(failureOperation)
                } else {
                    writeFailures.recordFailure(failureOperation)
                }
            } finally {
                outstandingWriteTracker.complete(outstandingTicket)
            }
        }
    }

    private companion object {
        const val WRITE_FAILURE_MESSAGE =
            "A medicine change could not be synchronised. Check your connection and try again."
    }
}
