package io.github.ffelixq.medswidget.widget

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.BuildConfig
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.data.CountdownWriteOutcome
import io.github.ffelixq.medswidget.data.DoseWriteOutcome
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownAction
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object WidgetActionParameters {
    val MEDICINE_ID = ActionParameters.Key<String>("medicine_id")
    val SLOT = ActionParameters.Key<String>("slot")
    val SOURCE = ActionParameters.Key<String>("source")
    val APP_WIDGET_ID = ActionParameters.Key<Int>("app_widget_id")
}

internal data class WidgetCheckRequest(
    val medicineId: String,
    val slot: DoseSlot,
    val source: CheckSource,
    val appWidgetId: Int?,
)

@Keep
class CheckDoseAction : ActionCallback {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetActionDiagnostics.record(WidgetActionDiagnostic.WIDGET_ACTION_RECEIVED)
        try {
            WidgetCheckHandler(
                dependencies = {
                    GraphWidgetCheckDependencies(MedsApplication.graph(context))
                },
                recordDiagnostic = WidgetActionDiagnostics::record,
            ).handle(parameters) {
                GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            }
        } catch (cancellation: CancellationException) {
            WidgetActionDiagnostics.record(WidgetActionDiagnostic.CANCELLED)
            throw cancellation
        } catch (failure: Exception) {
            WidgetActionDiagnostics.recordFailure(failure)
            throw failure
        }
    }
}

@Keep
class StartCountdownAction : ActionCallback {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetActionDiagnostics.record(WidgetActionDiagnostic.COUNTDOWN_ACTION_RECEIVED)
        try {
            WidgetCountdownHandler(
                dependencies = {
                    GraphWidgetCountdownDependencies(MedsApplication.graph(context))
                },
                recordDiagnostic = WidgetActionDiagnostics::record,
            ).handle(parameters) {
                GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            }
        } catch (cancellation: CancellationException) {
            WidgetActionDiagnostics.record(WidgetActionDiagnostic.CANCELLED)
            throw cancellation
        } catch (failure: Exception) {
            WidgetActionDiagnostics.recordFailure(failure)
            throw failure
        }
    }
}

internal object WidgetActionDiagnostic {
    const val WIDGET_ACTION_RECEIVED = "widget_action_received"
    const val COUNTDOWN_ACTION_RECEIVED = "countdown_action_received"
    const val PARAMETERS_VALID = "parameters_valid"
    const val WIDGET_ID_VALID = "widget_id_valid"
    const val AUTH_AVAILABLE = "auth_available"
    const val CONFIGURATION_VALID = "configuration_valid"
    const val SNAPSHOT_ELIGIBLE = "snapshot_eligible"
    const val OPTIMISTIC_UPDATE_APPLIED = "optimistic_update_applied"
    const val WIDGET_RENDER_REQUESTED = "widget_render_requested"
    const val REPOSITORY_WRITE_SUCCEEDED = "repository_write_succeeded"
    const val INVALID_PARAMETERS = "invalid_parameters"
    const val WIDGET_ID_MISMATCH = "widget_id_mismatch"
    const val AUTH_UNAVAILABLE = "auth_unavailable"
    const val CONFIGURATION_INVALID = "configuration_invalid"
    const val SNAPSHOT_MISSING = "snapshot_missing"
    const val MEDICINE_INELIGIBLE = "medicine_ineligible"
    const val OPTIMISTIC_UPDATE_REJECTED = "optimistic_update_rejected"
    const val REPOSITORY_WRITE_FAILED = "repository_write_failed"
    const val COUNTDOWN_UNAVAILABLE = "countdown_unavailable"
    const val CANCELLED = "cancelled"
    const val CALLBACK_FAILED = "callback_failed"
}

@Suppress("TooManyFunctions")
internal interface WidgetCountdownDependencies {
    val currentUid: String?
    val startedAt: Instant
    val timezoneId: String

    suspend fun refreshTemporalState()

    suspend fun configuration(id: Int): SingleWidgetConfiguration?

    suspend fun readSnapshot(): WidgetSnapshot

    suspend fun recoverFromRepositories()

    @Suppress("LongParameterList")
    suspend fun markStartedOptimistically(
        uid: String,
        logicalDay: LocalDate,
        medicineId: String,
        slot: DoseSlot,
        durationMinutes: Int,
        source: CheckSource,
        actionId: String,
        startedAt: Instant,
    ): Boolean

    fun schedulePendingReconciliation()

    suspend fun scheduleCountdownRefresh()

    suspend fun updateWidgets()

    @Suppress("LongParameterList")
    suspend fun start(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        startedAt: Instant,
        durationMinutes: Int,
    ): Boolean

    suspend fun markActionSubmitted(actionId: String)

    suspend fun rejectOptimisticAction(
        uid: String,
        actionId: String,
        medicineId: String,
        slot: DoseSlot,
    )
}

internal class WidgetCountdownHandler(
    private val recordDiagnostic: (String) -> Unit = {},
    private val dependencies: () -> WidgetCountdownDependencies,
) {
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    suspend fun handle(
        parameters: ActionParameters,
        resolveAppWidgetId: suspend () -> Int?,
    ) {
        val parsed =
            parameters.toWidgetCheckRequest()
                ?: return recordDiagnostic(WidgetActionDiagnostic.INVALID_PARAMETERS)
        recordDiagnostic(WidgetActionDiagnostic.PARAMETERS_VALID)
        val request =
            if (parsed.source == CheckSource.WIDGET_2X2) {
                val resolved = resolveAppWidgetId()
                if (parsed.appWidgetId != resolved) {
                    return recordDiagnostic(WidgetActionDiagnostic.WIDGET_ID_MISMATCH)
                }
                parsed.copy(appWidgetId = resolved)
            } else {
                parsed
            }
        recordDiagnostic(WidgetActionDiagnostic.WIDGET_ID_VALID)
        val dependencies = dependencies()
        dependencies.refreshTemporalState()
        val uid = dependencies.currentUid ?: return recordDiagnostic(WidgetActionDiagnostic.AUTH_UNAVAILABLE)
        recordDiagnostic(WidgetActionDiagnostic.AUTH_AVAILABLE)
        if (!request.hasValidConfiguration(dependencies.asCheckDependencies(), uid)) {
            return recordDiagnostic(WidgetActionDiagnostic.CONFIGURATION_INVALID)
        }
        recordDiagnostic(WidgetActionDiagnostic.CONFIGURATION_VALID)
        var snapshot = dependencies.readSnapshot()
        if (!snapshot.belongsTo(uid)) {
            recordDiagnostic(WidgetActionDiagnostic.SNAPSHOT_MISSING)
            dependencies.recoverFromRepositories()
            snapshot = dependencies.readSnapshot()
            if (!snapshot.belongsTo(uid)) return
        }
        val medicine =
            snapshot.eligibleMedicine(request)
                ?: return recordDiagnostic(WidgetActionDiagnostic.MEDICINE_INELIGIBLE)
        val row =
            snapshot.rows.firstOrNull {
                it.medicineId == request.medicineId && it.slot == request.slot
            }
        val duration =
            row
                ?.takeIf { !it.isTaken && it.countdown == null }
                ?.countdownMinutes
                ?: return recordDiagnostic(WidgetActionDiagnostic.COUNTDOWN_UNAVAILABLE)
        recordDiagnostic(WidgetActionDiagnostic.SNAPSHOT_ELIGIBLE)
        val actionId = UUID.randomUUID().toString()
        val startedAt = dependencies.startedAt
        if (
            !dependencies.markStartedOptimistically(
                uid,
                snapshot.logicalDay,
                request.medicineId,
                request.slot,
                duration,
                request.source,
                actionId,
                startedAt,
            )
        ) {
            return recordDiagnostic(WidgetActionDiagnostic.OPTIMISTIC_UPDATE_REJECTED)
        }
        recordDiagnostic(WidgetActionDiagnostic.OPTIMISTIC_UPDATE_APPLIED)
        try {
            dependencies.schedulePendingReconciliation()
            dependencies.scheduleCountdownRefresh()
            dependencies.updateWidgets()
            recordDiagnostic(WidgetActionDiagnostic.WIDGET_RENDER_REQUESTED)
            val applied =
                dependencies.start(
                    uid,
                    snapshot.logicalDay,
                    medicine.toDomain(uid),
                    request.slot,
                    request.source,
                    actionId,
                    startedAt,
                    duration,
                )
            if (applied) {
                dependencies.markActionSubmitted(actionId)
                recordDiagnostic(WidgetActionDiagnostic.REPOSITORY_WRITE_SUCCEEDED)
            } else {
                dependencies.rejectOptimisticAction(uid, actionId, request.medicineId, request.slot)
                dependencies.recoverFromRepositories()
                recordDiagnostic(WidgetActionDiagnostic.REPOSITORY_WRITE_FAILED)
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                dependencies.rejectOptimisticAction(uid, actionId, request.medicineId, request.slot)
                dependencies.recoverFromRepositories()
            }
            throw cancellation
        } catch (_: Exception) {
            dependencies.rejectOptimisticAction(uid, actionId, request.medicineId, request.slot)
            dependencies.recoverFromRepositories()
            recordDiagnostic(WidgetActionDiagnostic.REPOSITORY_WRITE_FAILED)
        }
    }
}

private fun WidgetCountdownDependencies.asCheckDependencies(): WidgetCheckDependencies =
    object : WidgetCheckDependencies {
        override val currentUid: String? get() = this@asCheckDependencies.currentUid
        override val checkedAt: Instant get() = this@asCheckDependencies.startedAt
        override val timezoneId: String get() = this@asCheckDependencies.timezoneId

        override suspend fun refreshTemporalState() = this@asCheckDependencies.refreshTemporalState()

        override suspend fun configuration(id: Int) = this@asCheckDependencies.configuration(id)

        override suspend fun readSnapshot() = this@asCheckDependencies.readSnapshot()

        override suspend fun recoverFromRepositories() = this@asCheckDependencies.recoverFromRepositories()

        override suspend fun markTakenOptimistically(
            uid: String,
            medicineId: String,
            slot: DoseSlot,
            checkedAt: Instant,
            timezoneId: String,
            actionId: String,
        ) = false

        override suspend fun updateWidgets() = this@asCheckDependencies.updateWidgets()

        override fun schedulePendingReconciliation() = this@asCheckDependencies.schedulePendingReconciliation()

        override suspend fun markActionSubmitted(actionId: String) = Unit

        override suspend fun check(
            uid: String,
            logicalDay: LocalDate,
            medicine: Medicine,
            slot: DoseSlot,
            source: CheckSource,
            actionId: String,
            occurredAt: Instant,
        ) = false

        override suspend fun clearCountdown(
            uid: String,
            medicineId: String,
            slot: DoseSlot,
            source: CheckSource,
            state: CountdownState?,
        ) = Unit

        override suspend fun rejectOptimisticAction(
            uid: String,
            actionId: String,
            medicineId: String,
            slot: DoseSlot,
        ) = Unit
    }

@Suppress("TooManyFunctions")
private class GraphWidgetCountdownDependencies(
    private val graph: AppGraph,
) : WidgetCountdownDependencies {
    override val currentUid: String?
        get() = if (graph.accountOperationGate.isDeletionInProgress) null else graph.currentAuthenticatedUid
    override val startedAt: Instant get() = graph.clock.instant()
    override val timezoneId: String get() = ZoneId.systemDefault().id

    override suspend fun refreshTemporalState() = graph.prepareTemporalStateForWidgetRender()

    override suspend fun configuration(id: Int) = graph.configurationStore.get(id)

    override suspend fun readSnapshot() = graph.snapshotStore.read()

    override suspend fun recoverFromRepositories() = graph.refreshFromRepositories()

    override suspend fun markStartedOptimistically(
        uid: String,
        logicalDay: LocalDate,
        medicineId: String,
        slot: DoseSlot,
        durationMinutes: Int,
        source: CheckSource,
        actionId: String,
        startedAt: Instant,
    ): Boolean =
        graph.snapshotStore.markCountdownStartedOptimistically(
            uid,
            medicineId,
            slot,
            logicalDay,
            durationMinutes,
            startedAt,
            timezoneId,
            source,
            actionId,
        )

    override fun schedulePendingReconciliation() = graph.pendingWidgetSyncScheduler.schedule()

    override suspend fun scheduleCountdownRefresh() {
        graph.countdownRefreshScheduler.schedule(graph.snapshotStore.read())
    }

    override suspend fun updateWidgets() = graph.widgetUpdater.updateAll()

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
        graph.accountOperationGate.runMutation {
            graph.repositories.countdowns.start(
                uid,
                logicalDay,
                medicine,
                slot,
                source,
                actionId,
                startedAt,
                durationMinutes,
            )
        } ?: false

    override suspend fun markActionSubmitted(actionId: String) {
        graph.snapshotStore.markCountdownActionSubmitted(actionId)
    }

    override suspend fun rejectOptimisticAction(
        uid: String,
        actionId: String,
        medicineId: String,
        slot: DoseSlot,
    ) {
        graph.snapshotStore.resolveCountdownWriteOutcome(
            CountdownWriteOutcome(
                ownerUid = uid,
                actionId = actionId,
                medicineId = medicineId,
                slot = slot,
                action = CountdownAction.START,
                successful = false,
                errorMessage = "The countdown could not be started. Try again.",
            ),
        )
    }
}

private object WidgetActionDiagnostics {
    private const val TAG = "MedsWidgetAction"

    fun record(code: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "stage=$code")
        }
    }

    fun recordFailure(failure: Exception) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "reason=${WidgetActionDiagnostic.CALLBACK_FAILED} type=${failure.javaClass.simpleName}")
        }
    }
}

@Suppress("TooManyFunctions")
internal interface WidgetCheckDependencies {
    val currentUid: String?
    val checkedAt: Instant
    val timezoneId: String

    suspend fun refreshTemporalState()

    suspend fun configuration(id: Int): SingleWidgetConfiguration?

    suspend fun readSnapshot(): WidgetSnapshot

    suspend fun markTakenOptimistically(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        checkedAt: Instant,
        timezoneId: String,
        actionId: String,
    ): Boolean

    suspend fun updateWidgets()

    fun schedulePendingReconciliation()

    suspend fun markActionSubmitted(actionId: String)

    suspend fun check(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        occurredAt: Instant,
    ): Boolean

    suspend fun clearCountdown(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        source: CheckSource,
        state: CountdownState?,
    ) = Unit

    suspend fun rejectOptimisticAction(
        uid: String,
        actionId: String,
        medicineId: String,
        slot: DoseSlot,
    )

    suspend fun recoverFromRepositories()
}

internal class WidgetCheckHandler(
    private val recordDiagnostic: (String) -> Unit = {},
    private val dependencies: () -> WidgetCheckDependencies,
) {
    @Suppress("ReturnCount")
    suspend fun handle(
        parameters: ActionParameters,
        resolveAppWidgetId: suspend () -> Int?,
    ) {
        val parsedRequest =
            parameters.toWidgetCheckRequest()
                ?: return recordDiagnostic(WidgetActionDiagnostic.INVALID_PARAMETERS)
        recordDiagnostic(WidgetActionDiagnostic.PARAMETERS_VALID)
        val request =
            if (parsedRequest.source == CheckSource.WIDGET_2X2) {
                val resolvedWidgetId = resolveAppWidgetId()
                if (parsedRequest.appWidgetId != resolvedWidgetId) {
                    return recordDiagnostic(WidgetActionDiagnostic.WIDGET_ID_MISMATCH)
                }
                parsedRequest.copy(appWidgetId = resolvedWidgetId)
            } else {
                parsedRequest
            }
        recordDiagnostic(WidgetActionDiagnostic.WIDGET_ID_VALID)

        val dependencies = dependencies()
        dependencies.refreshTemporalState()
        val uid =
            dependencies.currentUid
                ?: return recordDiagnostic(WidgetActionDiagnostic.AUTH_UNAVAILABLE)
        recordDiagnostic(WidgetActionDiagnostic.AUTH_AVAILABLE)
        if (!request.hasValidConfiguration(dependencies, uid)) {
            return recordDiagnostic(WidgetActionDiagnostic.CONFIGURATION_INVALID)
        }
        recordDiagnostic(WidgetActionDiagnostic.CONFIGURATION_VALID)

        var snapshot = dependencies.readSnapshot()
        if (!snapshot.belongsTo(uid)) {
            recordDiagnostic(WidgetActionDiagnostic.SNAPSHOT_MISSING)
            dependencies.recoverFromRepositories()
            snapshot = dependencies.readSnapshot()
            if (!snapshot.belongsTo(uid)) return
        }
        val cachedMedicine =
            snapshot.eligibleMedicine(request)
                ?: return recordDiagnostic(WidgetActionDiagnostic.MEDICINE_INELIGIBLE)
        recordDiagnostic(WidgetActionDiagnostic.SNAPSHOT_ELIGIBLE)
        val actionId = UUID.randomUUID().toString()
        val checkedAt = dependencies.checkedAt
        val changed =
            dependencies.markTakenOptimistically(
                uid = uid,
                medicineId = request.medicineId,
                slot = request.slot,
                checkedAt = checkedAt,
                timezoneId = dependencies.timezoneId,
                actionId = actionId,
            )
        if (!changed) {
            return recordDiagnostic(WidgetActionDiagnostic.OPTIMISTIC_UPDATE_REJECTED)
        }
        recordDiagnostic(WidgetActionDiagnostic.OPTIMISTIC_UPDATE_APPLIED)
        dependencies.scheduleAndRenderOrRollback(uid, actionId, request)
        recordDiagnostic(WidgetActionDiagnostic.WIDGET_RENDER_REQUESTED)
        val applied =
            dependencies.submitCheckOrRollback(
                uid = uid,
                actionId = actionId,
                request = request,
                snapshot = snapshot,
                medicine = cachedMedicine,
                checkedAt = checkedAt,
            )
        if (applied) {
            dependencies.clearCountdown(
                uid = uid,
                medicineId = request.medicineId,
                slot = request.slot,
                source = request.source,
                state =
                    snapshot.rows
                        .firstOrNull {
                            it.medicineId == request.medicineId && it.slot == request.slot
                        }?.countdown,
            )
            dependencies.markActionSubmitted(actionId)
            recordDiagnostic(WidgetActionDiagnostic.REPOSITORY_WRITE_SUCCEEDED)
        } else {
            recordDiagnostic(WidgetActionDiagnostic.REPOSITORY_WRITE_FAILED)
            dependencies.rejectAndRecover(uid, actionId, request)
        }
    }
}

@Suppress("TooManyFunctions")
private class GraphWidgetCheckDependencies(
    private val graph: AppGraph,
) : WidgetCheckDependencies {
    override val currentUid: String?
        get() =
            if (graph.accountOperationGate.isDeletionInProgress) {
                null
            } else {
                graph.currentAuthenticatedUid
            }

    override val checkedAt: Instant
        get() = graph.clock.instant()

    override val timezoneId: String
        get() = ZoneId.systemDefault().id

    override suspend fun refreshTemporalState() {
        graph.prepareTemporalStateForWidgetRender()
    }

    override suspend fun configuration(id: Int): SingleWidgetConfiguration? = graph.configurationStore.get(id)

    override suspend fun readSnapshot(): WidgetSnapshot = graph.snapshotStore.read()

    override suspend fun markTakenOptimistically(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        checkedAt: Instant,
        timezoneId: String,
        actionId: String,
    ): Boolean =
        graph.snapshotStore.markTakenOptimistically(
            expectedUid = uid,
            medicineId = medicineId,
            slot = slot,
            checkedAt = checkedAt,
            checkedTimezone = timezoneId,
            actionId = actionId,
        )

    override suspend fun updateWidgets() {
        graph.widgetUpdater.updateAll()
    }

    override fun schedulePendingReconciliation() {
        graph.pendingWidgetSyncScheduler.schedule()
    }

    override suspend fun markActionSubmitted(actionId: String) {
        graph.snapshotStore.markActionSubmitted(actionId)
    }

    override suspend fun check(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        occurredAt: Instant,
    ): Boolean =
        graph.accountOperationGate.runMutation {
            graph.repositories.doses.checkWithAction(
                uid = uid,
                logicalDay = logicalDay,
                medicine = medicine,
                slot = slot,
                source = source,
                actionId = actionId,
                occurredAt = occurredAt,
            )
        } ?: false

    override suspend fun rejectOptimisticAction(
        uid: String,
        actionId: String,
        medicineId: String,
        slot: DoseSlot,
    ) {
        graph.snapshotStore.resolveWriteOutcome(
            DoseWriteOutcome(
                ownerUid = uid,
                actionId = actionId,
                medicineId = medicineId,
                slot = slot,
                action = DoseAction.CHECK,
                successful = false,
                errorMessage = "The dose could not be checked. Try again.",
            ),
        )
    }

    override suspend fun clearCountdown(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        source: CheckSource,
        state: CountdownState?,
    ) {
        graph.accountOperationGate.runMutation {
            graph.repositories.countdowns.clearForDoseCheck(uid, medicineId, slot, source, state)
        }
    }

    override suspend fun recoverFromRepositories() {
        graph.refreshFromRepositories()
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun WidgetCheckDependencies.scheduleAndRenderOrRollback(
    uid: String,
    actionId: String,
    request: WidgetCheckRequest,
) {
    try {
        schedulePendingReconciliation()
        updateWidgets()
    } catch (cancellation: CancellationException) {
        rejectAndRecover(uid, actionId, request, cancellation)
        throw cancellation
    } catch (failure: Exception) {
        rejectAndRecover(uid, actionId, request, failure)
        throw failure
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun WidgetCheckDependencies.submitCheckOrRollback(
    uid: String,
    actionId: String,
    request: WidgetCheckRequest,
    snapshot: WidgetSnapshot,
    medicine: WidgetMedicine,
    checkedAt: Instant,
): Boolean =
    try {
        check(
            uid = uid,
            logicalDay = snapshot.logicalDay,
            medicine = medicine.toDomain(uid),
            slot = request.slot,
            source = request.source,
            actionId = actionId,
            occurredAt = checkedAt,
        )
    } catch (cancellation: CancellationException) {
        rejectAndRecover(uid, actionId, request, cancellation)
        throw cancellation
    } catch (_: Exception) {
        false
    }

@Suppress("TooGenericExceptionCaught")
private suspend fun WidgetCheckDependencies.rejectAndRecover(
    uid: String,
    actionId: String,
    request: WidgetCheckRequest,
    originalFailure: Throwable? = null,
) {
    try {
        withContext(NonCancellable) {
            try {
                rejectOptimisticAction(
                    uid = uid,
                    actionId = actionId,
                    medicineId = request.medicineId,
                    slot = request.slot,
                )
            } finally {
                recoverFromRepositories()
            }
        }
    } catch (cleanupFailure: Exception) {
        if (originalFailure != null) {
            originalFailure.addSuppressed(cleanupFailure)
        } else {
            throw cleanupFailure
        }
    }
}

internal fun ActionParameters.toWidgetCheckRequest(): WidgetCheckRequest? {
    val medicineId =
        this[WidgetActionParameters.MEDICINE_ID]
            ?.takeIf(String::isNotBlank)
            ?: return null
    val slot = this[WidgetActionParameters.SLOT]?.let(DoseSlot::fromWire) ?: return null
    val source = this[WidgetActionParameters.SOURCE]?.let(CheckSource::fromWire) ?: return null
    if (source != CheckSource.WIDGET_2X2 && source != CheckSource.WIDGET_4X2) return null
    val appWidgetId =
        if (source == CheckSource.WIDGET_2X2) {
            this[WidgetActionParameters.APP_WIDGET_ID]?.takeIf { it > 0 }
        } else {
            null
        }
    return WidgetCheckRequest(medicineId, slot, source, appWidgetId)
        .takeIf { source != CheckSource.WIDGET_2X2 || appWidgetId != null }
}

private suspend fun WidgetCheckRequest.hasValidConfiguration(
    dependencies: WidgetCheckDependencies,
    uid: String,
): Boolean {
    if (source != CheckSource.WIDGET_2X2) return true
    val widgetId = appWidgetId ?: return false
    val configuration = dependencies.configuration(widgetId) ?: return false
    return configuration.ownerUid == uid && configuration.medicineId == medicineId
}

private fun WidgetSnapshot.eligibleMedicine(request: WidgetCheckRequest): WidgetMedicine? {
    val medicine = medicine(request.medicineId) ?: return null
    val enabled =
        when (request.slot) {
            DoseSlot.AFTERNOON -> medicine.afternoonEnabled
            DoseSlot.NIGHT -> medicine.nightEnabled
        }
    return medicine.takeIf { enabled }
}

private fun WidgetSnapshot.belongsTo(uid: String): Boolean = signedIn && ownerUid == uid

private fun WidgetMedicine.toDomain(uid: String): Medicine =
    Medicine(
        id = id,
        ownerUid = uid,
        name = name,
        afternoonEnabled = afternoonEnabled,
        afternoonLabel = afternoonLabel,
        afternoonCountdownMinutes = afternoonCountdownMinutes,
        nightEnabled = nightEnabled,
        nightLabel = nightLabel,
        nightCountdownMinutes = nightCountdownMinutes,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
