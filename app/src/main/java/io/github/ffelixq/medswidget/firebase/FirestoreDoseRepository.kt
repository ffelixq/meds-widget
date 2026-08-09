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
import io.github.ffelixq.medswidget.domain.DOSE_SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseActionPolicy
import io.github.ffelixq.medswidget.domain.DoseCommandDecision
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseIds
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.Medicine
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

@Suppress("TooManyFunctions")
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
                                values.forEach { state ->
                                    activeState[actionKey(uid, state.id)] = state
                                }
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
            uid,
            logicalDay,
            medicine,
            slot,
            source,
            actionId,
            occurredAt,
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
                    skippedAt = null,
                    skipReason = null,
                    undoneAt = null,
                    lastActionId = actionId,
                )
            activeState[stateKey] = state
            writeAction(
                DoseWriteRequest(
                    state = state,
                    rollbackState = previous,
                    actionId = actionId,
                    action = DoseAction.CHECK,
                    occurredAt = occurredAt,
                    timezoneId = zone,
                    source = source,
                    medicine = medicine,
                ),
            )
            true
        }

    override suspend fun skip(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        reason: String,
        source: CheckSource,
    ): Boolean =
        actionMutex.withLock {
            if (source != CheckSource.APP) return@withLock false
            val stateId = DoseIds.stateId(logicalDay, medicine.id, slot)
            val stateKey = actionKey(uid, stateId)
            val previous = activeState[stateKey]
            if (DoseActionPolicy.skip(previous) != DoseCommandDecision.APPLY_SKIP) {
                return@withLock false
            }
            val now = clock.instant()
            val zone = ZoneId.systemDefault().id
            val actionId = UUID.randomUUID().toString()
            val normalizedReason =
                reason
                    .trim()
                    .take(MAX_SKIP_REASON_LENGTH)
                    .ifBlank { null }
            val state =
                DoseState(
                    id = stateId,
                    ownerUid = uid,
                    logicalDay = logicalDay,
                    medicineId = medicine.id,
                    slot = slot,
                    labelSnapshot = medicine.label(slot),
                    medicineNameSnapshot = medicine.name,
                    isTaken = false,
                    checkedAt = null,
                    checkedTimezone = null,
                    checkedSource = null,
                    skippedAt = now,
                    skipReason = normalizedReason,
                    undoneAt = null,
                    lastActionId = actionId,
                )
            activeState[stateKey] = state
            writeAction(
                DoseWriteRequest(
                    state = state,
                    rollbackState = previous,
                    actionId = actionId,
                    action = DoseAction.SKIP,
                    occurredAt = now,
                    timezoneId = zone,
                    source = source,
                ),
            )
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
            val decision = DoseActionPolicy.undo(current, source)
            if (decision != DoseCommandDecision.APPLY_UNDO || current == null) {
                return@withLock false
            }
            val now = clock.instant()
            val zone = ZoneId.systemDefault().id
            val actionId = UUID.randomUUID().toString()
            val updated =
                current.copy(
                    isTaken = false,
                    skippedAt = null,
                    skipReason = null,
                    undoneAt = now,
                    lastActionId = actionId,
                )
            activeState[stateKey] = updated
            writeAction(
                DoseWriteRequest(
                    state = updated,
                    rollbackState = current,
                    actionId = actionId,
                    action = DoseAction.UNDO,
                    occurredAt = now,
                    timezoneId = zone,
                    source = source,
                    medicine = medicine,
                ),
            )
            true
        }

    private data class DoseWriteRequest(
        val state: DoseState,
        val rollbackState: DoseState?,
        val actionId: String,
        val action: DoseAction,
        val occurredAt: Instant,
        val timezoneId: String,
        val source: CheckSource,
        val medicine: Medicine? = null,
    )

    private fun writeAction(request: DoseWriteRequest) {
        val state = request.state
        val rollbackState = request.rollbackState
        val actionId = request.actionId
        val action = request.action
        val occurredAt = request.occurredAt
        val timezoneId = request.timezoneId
        val source = request.source
        val medicine = request.medicine
        val stateReference =
            FirestorePaths
                .doseStates(firestore, state.ownerUid)
                .document(state.id)
        val eventReference =
            FirestorePaths
                .doseEvents(firestore, state.ownerUid)
                .document(actionId)
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
                "skippedAt" to state.skippedAt?.let { Timestamp(Date.from(it)) },
                "skipReason" to state.skipReason,
                "undoneAt" to state.undoneAt?.let { Timestamp(Date.from(it)) },
                "lastActionId" to actionId,
                "updatedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to DOSE_SCHEMA_VERSION,
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
                "previousActionId" to previousActionId(action, rollbackState),
                "skipReason" to if (action == DoseAction.SKIP) state.skipReason else null,
                "syncedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to DOSE_SCHEMA_VERSION,
            ),
        )
        applySupplyAdjustment(batch, state.ownerUid, medicine, action, rollbackState)
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

    private fun applySupplyAdjustment(
        batch: WriteBatch,
        uid: String,
        medicine: Medicine?,
        action: DoseAction,
        previous: DoseState?,
    ) {
        if (medicine?.supplyEnabled != true || medicine.supplyInitialUnits == null) return
        val delta =
            when {
                action == DoseAction.CHECK -> -medicine.unitsPerDose
                action == DoseAction.UNDO && previous?.isTaken == true -> medicine.unitsPerDose
                else -> 0.0
            }
        if (delta == 0.0) return
        batch.update(
            FirestorePaths.medicines(firestore, uid).document(medicine.id),
            mapOf(
                "supplyInitialUnits" to FieldValue.increment(delta),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun dispatchWrite(
        batch: WriteBatch,
        pendingWrite: PendingWrite,
    ) {
        try {
            batch.commit().addOnCompleteListener { completedTask ->
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
                        pendingWrite.actionId,
                        pendingWrite.action,
                        true,
                    ),
                )
            } else {
                writeFailures.recordFailure(pendingWrite.failureOperation)
                rollbackOptimisticState(pendingWrite)
                onWriteOutcome(
                    pendingWrite.state.toWriteOutcome(
                        pendingWrite.actionId,
                        pendingWrite.action,
                        false,
                        WRITE_FAILURE_MESSAGE,
                    ),
                )
            }
        } finally {
            outstandingWriteTracker.complete(pendingWrite.outstandingTicket)
        }
    }

    private fun rollbackOptimisticState(pendingWrite: PendingWrite) {
        val state = pendingWrite.state
        val stateKey = actionKey(state.ownerUid, state.id)
        val rollbackState = pendingWrite.rollbackState
        if (rollbackState == null) {
            activeState.remove(stateKey, state)
        } else {
            activeState.replace(stateKey, state, rollbackState)
        }
    }

    private fun <T> withWriteFailure(
        uid: String,
        source: Flow<DataEnvelope<T>>,
    ): Flow<DataEnvelope<T>> =
        combine(source, writeFailures.observe(uid)) { envelope, failure ->
            envelope.copy(errorMessage = failure ?: envelope.errorMessage)
        }

    private fun previousActionId(
        action: DoseAction,
        rollbackState: DoseState?,
    ): String? = if (action == DoseAction.UNDO) rollbackState?.lastActionId else null

    private companion object {
        const val HISTORY_LIMIT = 1000L
        const val MAX_SKIP_REASON_LENGTH = 120
        const val WRITE_FAILURE_MESSAGE =
            "A dose change could not be synchronised. " +
                "Check your connection and try again."

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
