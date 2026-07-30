package io.github.ffelixq.medswidget.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import io.github.ffelixq.medswidget.data.DoseRepository
import io.github.ffelixq.medswidget.data.DoseWriteOutcome
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseActionPolicy
import io.github.ffelixq.medswidget.domain.DoseCommandDecision
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseIds
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.SCHEMA_VERSION
import io.github.ffelixq.medswidget.sync.OutstandingWriteTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FirestoreDoseRepository(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
    private val onWriteOutcome: (DoseWriteOutcome) -> Unit = {},
    private val outstandingWriteTracker: OutstandingWriteTracker = OutstandingWriteTracker(),
) : DoseRepository {
    private val actionMutex = Mutex()
    private val activeState = ConcurrentHashMap<String, DoseState>()
    private val writeFailures = UidScopedWriteFailures(WRITE_FAILURE_MESSAGE)

    override fun observeDay(
        uid: String,
        logicalDay: LocalDate,
    ): Flow<DataEnvelope<List<DoseState>>> =
        withWriteFailure(
            uid,
            callbackFlow {
                var lastValue = emptyList<DoseState>()
                val registration =
                    FirestorePaths
                        .doseStates(firestore, uid)
                        .whereEqualTo("logicalDay", logicalDay.toString())
                        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                            if (error != null) {
                                trySend(
                                    DataEnvelope(
                                        value = lastValue,
                                        fromCache = true,
                                        errorMessage = "Dose status could not be refreshed.",
                                    ),
                                )
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                writeFailures.recordHealthySnapshot(uid)
                                val values = snapshot.documents.mapNotNull { it.toDoseState() }
                                lastValue = values
                                val returnedKeys = values.map { actionKey(uid, it.id) }.toSet()
                                activeState.entries.removeIf { entry ->
                                    entry.value.ownerUid == uid &&
                                        entry.value.logicalDay == logicalDay &&
                                        entry.key !in returnedKeys
                                }
                                values.forEach { activeState[actionKey(uid, it.id)] = it }
                                trySend(
                                    DataEnvelope(
                                        value = values,
                                        fromCache = snapshot.metadata.isFromCache,
                                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                    ),
                                )
                            }
                        }
                awaitClose { registration.remove() }
            },
        )

    override fun observeHistory(uid: String): Flow<DataEnvelope<List<DoseEvent>>> =
        withWriteFailure(
            uid,
            callbackFlow {
                var lastValue = emptyList<DoseEvent>()
                val registration =
                    FirestorePaths
                        .doseEvents(firestore, uid)
                        .orderBy("syncedAt", Query.Direction.DESCENDING)
                        .limit(HISTORY_LIMIT)
                        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                            if (error != null) {
                                trySend(
                                    DataEnvelope(
                                        value = lastValue,
                                        fromCache = true,
                                        errorMessage = "History could not be refreshed.",
                                    ),
                                )
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                writeFailures.recordHealthySnapshot(uid)
                                lastValue = snapshot.documents.mapNotNull { it.toDoseEvent() }
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
            },
        )

    override suspend fun check(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
    ): Boolean =
        checkWithMetadata(
            uid = uid,
            logicalDay = logicalDay,
            medicine = medicine,
            slot = slot,
            source = source,
            actionId = UUID.randomUUID().toString(),
            occurredAt = clock.instant(),
        )

    override suspend fun checkWithAction(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        occurredAt: Instant,
    ): Boolean =
        checkWithMetadata(
            uid = uid,
            logicalDay = logicalDay,
            medicine = medicine,
            slot = slot,
            source = source,
            actionId = actionId,
            occurredAt = occurredAt,
        )

    private suspend fun checkWithMetadata(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        occurredAt: Instant,
    ): Boolean =
        actionMutex.withLock {
            val stateId = DoseIds.stateId(logicalDay, medicine.id, slot)
            val stateKey = actionKey(uid, stateId)
            val previous = activeState[stateKey]
            if (DoseActionPolicy.check(previous) != DoseCommandDecision.APPLY_CHECK) {
                return@withLock false
            }
            val zone = ZoneId.systemDefault().id
            val state =
                DoseState(
                    id = stateId,
                    ownerUid = uid,
                    logicalDay = logicalDay,
                    medicineId = medicine.id,
                    slot = slot,
                    labelSnapshot = medicine.label(slot),
                    medicineNameSnapshot = medicine.name,
                    isTaken = true,
                    checkedAt = occurredAt,
                    checkedTimezone = zone,
                    checkedSource = source,
                    undoneAt = null,
                    lastActionId = actionId,
                )
            activeState[stateKey] = state
            writeAction(state, previous, actionId, DoseAction.CHECK, occurredAt, zone, source)
            true
        }

    override suspend fun undo(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
    ): Boolean =
        actionMutex.withLock {
            val stateId = DoseIds.stateId(logicalDay, medicine.id, slot)
            val stateKey = actionKey(uid, stateId)
            val current = activeState[stateKey]
            if (DoseActionPolicy.undo(current, source) != DoseCommandDecision.APPLY_UNDO || current == null) {
                return@withLock false
            }
            val now = clock.instant()
            val zone = ZoneId.systemDefault().id
            val actionId = UUID.randomUUID().toString()
            val updated =
                current.copy(
                    isTaken = false,
                    undoneAt = now,
                    lastActionId = actionId,
                )
            activeState[stateKey] = updated
            writeAction(updated, current, actionId, DoseAction.UNDO, now, zone, source)
            true
        }

    private fun writeAction(
        state: DoseState,
        rollbackState: DoseState?,
        actionId: String,
        action: DoseAction,
        occurredAt: java.time.Instant,
        timezoneId: String,
        source: CheckSource,
    ) {
        val stateReference = FirestorePaths.doseStates(firestore, state.ownerUid).document(state.id)
        val eventReference = FirestorePaths.doseEvents(firestore, state.ownerUid).document(actionId)
        val batch = firestore.batch()
        batch.set(
            stateReference,
            mapOf(
                "ownerUid" to state.ownerUid,
                "logicalDay" to state.logicalDay.toString(),
                "medicineId" to state.medicineId,
                "slot" to state.slot.wireValue,
                "labelSnapshot" to state.labelSnapshot,
                "medicineNameSnapshot" to state.medicineNameSnapshot,
                "isTaken" to state.isTaken,
                "checkedAt" to state.checkedAt?.let { Timestamp(Date.from(it)) },
                "checkedTimezone" to state.checkedTimezone,
                "checkedSource" to state.checkedSource?.wireValue,
                "undoneAt" to state.undoneAt?.let { Timestamp(Date.from(it)) },
                "lastActionId" to actionId,
                "updatedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to SCHEMA_VERSION,
            ),
        )
        batch.set(
            eventReference,
            mapOf(
                "eventId" to actionId,
                "ownerUid" to state.ownerUid,
                "action" to action.wireValue,
                "logicalDay" to state.logicalDay.toString(),
                "medicineId" to state.medicineId,
                "medicineNameSnapshot" to state.medicineNameSnapshot,
                "slot" to state.slot.wireValue,
                "labelSnapshot" to state.labelSnapshot,
                "occurredAt" to Timestamp(Date.from(occurredAt)),
                "timezoneId" to timezoneId,
                "source" to source.wireValue,
                "relatedStateId" to state.id,
                "previousActionId" to
                    if (action == DoseAction.UNDO) {
                        rollbackState?.lastActionId
                    } else {
                        null
                    },
                "syncedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to SCHEMA_VERSION,
            ),
        )
        // Firestore persists the batch locally and synchronises it later. Avoid
        // awaiting server acknowledgement so an offline tap remains immediate.
        val pendingWrite =
            PendingWrite(
                state = state,
                rollbackState = rollbackState,
                actionId = actionId,
                action = action,
                failureOperation = writeFailures.begin(state.ownerUid),
                outstandingTicket = outstandingWriteTracker.begin(state.ownerUid),
            )
        dispatchWrite(batch, pendingWrite)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun dispatchWrite(
        batch: WriteBatch,
        pendingWrite: PendingWrite,
    ) {
        try {
            batch
                .commit()
                .addOnCompleteListener { completedTask ->
                    completeWrite(pendingWrite, completedTask.isSuccessful)
                }
        } catch (error: RuntimeException) {
            completeWrite(pendingWrite, successful = false)
            throw error
        }
    }

    private fun completeWrite(
        pendingWrite: PendingWrite,
        successful: Boolean,
    ) {
        try {
            if (successful) {
                writeFailures.recordSuccess(pendingWrite.failureOperation)
                onWriteOutcome(
                    pendingWrite.state.toWriteOutcome(
                        actionId = pendingWrite.actionId,
                        action = pendingWrite.action,
                        successful = true,
                    ),
                )
            } else {
                writeFailures.recordFailure(pendingWrite.failureOperation)
                val state = pendingWrite.state
                val stateKey = actionKey(state.ownerUid, state.id)
                val rollbackState = pendingWrite.rollbackState
                if (rollbackState == null) {
                    activeState.remove(stateKey, state)
                } else {
                    activeState.replace(stateKey, state, rollbackState)
                }
                onWriteOutcome(
                    state.toWriteOutcome(
                        actionId = pendingWrite.actionId,
                        action = pendingWrite.action,
                        successful = false,
                        errorMessage = WRITE_FAILURE_MESSAGE,
                    ),
                )
            }
        } finally {
            outstandingWriteTracker.complete(pendingWrite.outstandingTicket)
        }
    }

    private fun <T> withWriteFailure(
        uid: String,
        source: Flow<DataEnvelope<T>>,
    ): Flow<DataEnvelope<T>> =
        combine(source, writeFailures.observe(uid)) { envelope, failure ->
            envelope.copy(errorMessage = failure ?: envelope.errorMessage)
        }

    private companion object {
        const val HISTORY_LIMIT = 500L
        const val WRITE_FAILURE_MESSAGE =
            "A dose change could not be synchronised. Check your connection and try again."

        fun actionKey(
            uid: String,
            stateId: String,
        ): String = "$uid|$stateId"
    }

    private data class PendingWrite(
        val state: DoseState,
        val rollbackState: DoseState?,
        val actionId: String,
        val action: DoseAction,
        val failureOperation: UidScopedWriteFailures.Operation,
        val outstandingTicket: OutstandingWriteTracker.Ticket,
    )
}

private fun DoseState.toWriteOutcome(
    actionId: String,
    action: DoseAction,
    successful: Boolean,
    errorMessage: String? = null,
): DoseWriteOutcome =
    DoseWriteOutcome(
        ownerUid = ownerUid,
        actionId = actionId,
        medicineId = medicineId,
        slot = slot,
        action = action,
        successful = successful,
        errorMessage = errorMessage,
    )
