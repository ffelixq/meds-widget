package io.github.ffelixq.medswidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.data.DoseWriteOutcome
import io.github.ffelixq.medswidget.domain.CheckSource
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

class CheckDoseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetCheckHandler {
            GraphWidgetCheckDependencies(MedsApplication.graph(context))
        }.handle(parameters) {
            GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        }
    }
}

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

    suspend fun rejectOptimisticAction(
        uid: String,
        actionId: String,
        medicineId: String,
        slot: DoseSlot,
    )

    suspend fun recoverFromRepositories()
}

internal class WidgetCheckHandler(
    private val dependencies: () -> WidgetCheckDependencies,
) {
    suspend fun handle(
        parameters: ActionParameters,
        resolveAppWidgetId: suspend () -> Int?,
    ) {
        val request = parameters.toWidgetCheckRequest() ?: return
        if (
            request.source == CheckSource.WIDGET_2X2 &&
            request.appWidgetId != resolveAppWidgetId()
        ) {
            return
        }

        val dependencies = dependencies()
        dependencies.refreshTemporalState()
        val uid = dependencies.currentUid ?: return
        if (!request.hasValidConfiguration(dependencies, uid)) return

        val snapshot = dependencies.readSnapshot()
        val cachedMedicine = snapshot.eligibleMedicine(uid, request) ?: return
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
        if (changed) {
            dependencies.scheduleAndRenderOrRollback(uid, actionId, request)
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
                dependencies.markActionSubmitted(actionId)
            } else {
                dependencies.rejectAndRecover(uid, actionId, request)
            }
        }
    }
}

private class GraphWidgetCheckDependencies(
    private val graph: AppGraph,
) : WidgetCheckDependencies {
    override val currentUid: String?
        get() =
            if (graph.accountOperationGate.isDeletionInProgress) {
                null
            } else {
                graph.repositories.auth.session.value
                    ?.uid
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

private fun WidgetSnapshot.eligibleMedicine(
    uid: String,
    request: WidgetCheckRequest,
): WidgetMedicine? {
    if (!signedIn || ownerUid != uid) return null
    val medicine = medicine(request.medicineId) ?: return null
    val enabled =
        when (request.slot) {
            DoseSlot.AFTERNOON -> medicine.afternoonEnabled
            DoseSlot.NIGHT -> medicine.nightEnabled
        }
    return medicine.takeIf { enabled }
}

private fun WidgetMedicine.toDomain(uid: String): Medicine =
    Medicine(
        id = id,
        ownerUid = uid,
        name = name,
        afternoonEnabled = afternoonEnabled,
        afternoonLabel = afternoonLabel,
        nightEnabled = nightEnabled,
        nightLabel = nightLabel,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
