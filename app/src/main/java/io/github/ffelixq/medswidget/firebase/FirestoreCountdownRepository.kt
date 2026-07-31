package io.github.ffelixq.medswidget.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import io.github.ffelixq.medswidget.data.CountdownRepository
import io.github.ffelixq.medswidget.data.CountdownWriteOutcome
import io.github.ffelixq.medswidget.domain.COUNTDOWN_SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownAction
import io.github.ffelixq.medswidget.domain.CountdownIds
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.CountdownStatus
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.sync.OutstandingWriteTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FirestoreCountdownRepository(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
    private val onWriteOutcome: (CountdownWriteOutcome) -> Unit = {},
    private val outstandingWriteTracker: OutstandingWriteTracker = OutstandingWriteTracker(),
) : CountdownRepository {
    private val actionMutex = Mutex()
    private val activeState = ConcurrentHashMap<String, CountdownState>()
    private val writeFailures = UidScopedWriteFailures(WRITE_FAILURE_MESSAGE)

    override fun observeActive(uid: String): Flow<DataEnvelope<List<CountdownState>>> =
        combine(
            callbackFlow {
                var lastValue = emptyList<CountdownState>()
                val registration =
                    FirestorePaths
                        .countdownStates(firestore, uid)
                        .whereEqualTo("status", CountdownStatus.RUNNING.wireValue)
                        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                            if (error != null) {
                                trySend(
                                    DataEnvelope(
                                        value = lastValue,
                                        fromCache = true,
                                        errorMessage = "Countdowns could not be refreshed.",
                                    ),
                                )
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                writeFailures.recordHealthySnapshot(uid)
                                val values = snapshot.documents.mapNotNull { it.toCountdownState() }
                                lastValue = values
                                val returnedIds = values.map(CountdownState::id).toSet()
                                activeState.entries.removeIf {
                                    it.value.ownerUid == uid && it.value.id !in returnedIds
                                }
                                values.forEach { activeState[key(uid, it.id)] = it }
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
            writeFailures.observe(uid),
        ) { envelope, failure ->
            envelope.copy(errorMessage = failure ?: envelope.errorMessage)
        }

    override suspend fun start(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        startedAt: Instant,
        durationMinutes: Int,
    ): Boolean =
        actionMutex.withLock {
            if (!medicine.isEnabled(slot) || medicine.countdownMinutes(slot) != durationMinutes) {
                return@withLock false
            }
            if (activeState.values.hasUnresolvedCountdown(uid, medicine.id, slot)) {
                return@withLock false
            }
            val stateId = CountdownIds.stateId(logicalDay, medicine.id, slot)
            val stateKey = key(uid, stateId)
            val stateReference = FirestorePaths.countdownStates(firestore, uid).document(stateId)
            val previous =
                activeState[stateKey]
                    ?: runCatching { stateReference.get(Source.CACHE).await().toCountdownState() }.getOrNull()
            if (previous?.status == CountdownStatus.RUNNING) {
                return@withLock false
            }
            val state =
                CountdownState(
                    id = stateId,
                    ownerUid = uid,
                    logicalDay = logicalDay,
                    medicineId = medicine.id,
                    slot = slot,
                    durationMinutes = durationMinutes,
                    startedAt = startedAt,
                    targetAt = CountdownLogic.targetAt(startedAt, durationMinutes),
                    startedTimezone = ZoneId.systemDefault().id,
                    startedSource = source,
                    status = CountdownStatus.RUNNING,
                    cancelledAt = null,
                    completedAt = null,
                    lastActionId = actionId,
                )
            activeState[stateKey] = state
            write(state, previous, CountdownAction.START, source, startedAt)
            true
        }

    override suspend fun cancel(
        uid: String,
        state: CountdownState,
        source: CheckSource,
    ): Boolean = transition(uid, state, state.durationMinutes, CountdownAction.CANCEL, source)

    override suspend fun restart(
        uid: String,
        state: CountdownState,
        durationMinutes: Int,
        source: CheckSource,
    ): Boolean = transition(uid, state, durationMinutes, CountdownAction.RESTART, source)

    override suspend fun clearForDoseCheck(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        source: CheckSource,
        state: CountdownState?,
    ): Boolean =
        actionMutex.withLock {
            val current =
                activeState.values.firstOrNull {
                    it.ownerUid == uid &&
                        it.medicineId == medicineId &&
                        it.slot == slot &&
                        it.status == CountdownStatus.RUNNING
                } ?: state?.takeIf {
                    it.ownerUid == uid &&
                        it.medicineId == medicineId &&
                        it.slot == slot &&
                        it.status == CountdownStatus.RUNNING
                } ?: return@withLock false
            activeState[key(uid, current.id)] = current
            applyTransition(current, current.durationMinutes, CountdownAction.CLEAR_BY_CHECK, source)
            true
        }

    private suspend fun transition(
        uid: String,
        supplied: CountdownState,
        durationMinutes: Int,
        action: CountdownAction,
        source: CheckSource,
    ): Boolean =
        actionMutex.withLock {
            val current = activeState[key(uid, supplied.id)] ?: return@withLock false
            if (current.status != CountdownStatus.RUNNING || current.lastActionId != supplied.lastActionId) {
                return@withLock false
            }
            applyTransition(current, durationMinutes, action, source)
            true
        }

    private fun applyTransition(
        current: CountdownState,
        durationMinutes: Int,
        action: CountdownAction,
        source: CheckSource,
    ) {
        val occurredAt = clock.instant()
        val actionId = UUID.randomUUID().toString()
        val updated =
            when (action) {
                CountdownAction.RESTART -> {
                    current.copy(
                        durationMinutes = durationMinutes,
                        startedAt = occurredAt,
                        targetAt = CountdownLogic.targetAt(occurredAt, durationMinutes),
                        startedTimezone = ZoneId.systemDefault().id,
                        startedSource = source,
                        cancelledAt = null,
                        completedAt = null,
                        lastActionId = actionId,
                    )
                }

                CountdownAction.CANCEL -> {
                    current.copy(
                        status = CountdownStatus.CANCELLED,
                        cancelledAt = occurredAt,
                        lastActionId = actionId,
                    )
                }

                CountdownAction.CLEAR_BY_CHECK -> {
                    current.copy(
                        status = CountdownStatus.CONSUMED,
                        completedAt = occurredAt,
                        lastActionId = actionId,
                    )
                }

                CountdownAction.START -> {
                    error("Start is created separately.")
                }
            }
        activeState[key(current.ownerUid, current.id)] = updated
        write(updated, current, action, source, occurredAt)
    }

    private fun write(
        state: CountdownState,
        rollbackState: CountdownState?,
        action: CountdownAction,
        source: CheckSource,
        occurredAt: Instant,
    ) {
        val stateReference = FirestorePaths.countdownStates(firestore, state.ownerUid).document(state.id)
        val eventReference = FirestorePaths.countdownEvents(firestore, state.ownerUid).document(state.lastActionId)
        val batch = firestore.batch()
        batch.set(
            stateReference,
            mapOf(
                "ownerUid" to state.ownerUid,
                "logicalDay" to state.logicalDay.toString(),
                "medicineId" to state.medicineId,
                "slot" to state.slot.wireValue,
                "durationMinutes" to state.durationMinutes,
                "startedAt" to Timestamp(Date.from(state.startedAt)),
                "targetAt" to Timestamp(Date.from(state.targetAt)),
                "startedTimezone" to state.startedTimezone,
                "startedSource" to state.startedSource.wireValue,
                "status" to state.status.wireValue,
                "cancelledAt" to state.cancelledAt?.let { Timestamp(Date.from(it)) },
                "completedAt" to state.completedAt?.let { Timestamp(Date.from(it)) },
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastActionId" to state.lastActionId,
                "schemaVersion" to COUNTDOWN_SCHEMA_VERSION,
            ),
        )
        batch.set(
            eventReference,
            mapOf(
                "eventId" to state.lastActionId,
                "ownerUid" to state.ownerUid,
                "action" to action.wireValue,
                "logicalDay" to state.logicalDay.toString(),
                "medicineId" to state.medicineId,
                "slot" to state.slot.wireValue,
                "durationMinutes" to state.durationMinutes,
                "occurredAt" to Timestamp(Date.from(occurredAt)),
                "timezoneId" to ZoneId.systemDefault().id,
                "source" to source.wireValue,
                "relatedStateId" to state.id,
                "previousActionId" to rollbackState?.lastActionId,
                "syncedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to COUNTDOWN_SCHEMA_VERSION,
            ),
        )
        val pending =
            PendingWrite(
                state,
                rollbackState,
                action,
                writeFailures.begin(state.ownerUid),
                outstandingWriteTracker.begin(state.ownerUid),
            )
        dispatch(batch, pending)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun dispatch(
        batch: WriteBatch,
        pending: PendingWrite,
    ) {
        try {
            batch.commit().addOnCompleteListener { complete(pending, it.isSuccessful) }
        } catch (error: RuntimeException) {
            complete(pending, successful = false)
            throw error
        }
    }

    private fun complete(
        pending: PendingWrite,
        successful: Boolean,
    ) {
        try {
            if (successful) {
                writeFailures.recordSuccess(pending.failureOperation)
            } else {
                writeFailures.recordFailure(pending.failureOperation)
                val stateKey = key(pending.state.ownerUid, pending.state.id)
                if (pending.rollbackState == null) {
                    activeState.remove(stateKey, pending.state)
                } else {
                    activeState.replace(stateKey, pending.state, pending.rollbackState)
                }
            }
            onWriteOutcome(
                CountdownWriteOutcome(
                    ownerUid = pending.state.ownerUid,
                    actionId = pending.state.lastActionId,
                    medicineId = pending.state.medicineId,
                    slot = pending.state.slot,
                    action = pending.action,
                    successful = successful,
                    errorMessage = if (successful) null else WRITE_FAILURE_MESSAGE,
                ),
            )
        } finally {
            outstandingWriteTracker.complete(pending.outstandingTicket)
        }
    }

    private data class PendingWrite(
        val state: CountdownState,
        val rollbackState: CountdownState?,
        val action: CountdownAction,
        val failureOperation: UidScopedWriteFailures.Operation,
        val outstandingTicket: OutstandingWriteTracker.Ticket,
    )

    private companion object {
        const val WRITE_FAILURE_MESSAGE =
            "A countdown change could not be synchronised. Check your connection and try again."

        fun key(
            uid: String,
            stateId: String,
        ): String = "$uid|$stateId"
    }
}

@Suppress("ComplexCondition")
private fun Collection<CountdownState>.hasUnresolvedCountdown(
    uid: String,
    medicineId: String,
    slot: DoseSlot,
): Boolean =
    any {
        it.ownerUid == uid &&
            it.medicineId == medicineId &&
            it.slot == slot &&
            it.status == CountdownStatus.RUNNING
    }
