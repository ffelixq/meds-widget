package io.github.ffelixq.medswidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ffelixq.medswidget.AccountDaySnapshot
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.data.RepositoryBundle
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CompletionProgress
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.DoseRows
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.LogicalDayCalculator
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.MedicineValidator
import io.github.ffelixq.medswidget.domain.ValidationResult
import io.github.ffelixq.medswidget.sync.AccountOperationGate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class MainUiState(
    val isLoading: Boolean = true,
    val logicalDay: LocalDate = LocalDate.now(),
    val medicines: List<Medicine> = emptyList(),
    val rows: List<DoseRow> = emptyList(),
    val progress: CompletionProgress = CompletionProgress(0, 0),
    val isCached: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val errorMessage: String? = null,
)

internal data class MainViewModelDependencies(
    val repositories: RepositoryBundle,
    val clock: Clock,
    val accountDaySnapshot: StateFlow<AccountDaySnapshot?>,
    val refreshTemporalState: suspend () -> Unit,
    val refreshFromRepositories: suspend () -> Unit,
    val accountOperationGate: AccountOperationGate = AccountOperationGate(),
)

@Suppress("TooManyFunctions")
class MainViewModel internal constructor(
    private val dependencies: MainViewModelDependencies,
) : ViewModel() {
    constructor(graph: AppGraph) : this(
        MainViewModelDependencies(
            repositories = graph.repositories,
            clock = graph.clock,
            accountDaySnapshot = graph.accountDaySnapshot,
            refreshTemporalState = graph::refreshTemporalState,
            refreshFromRepositories = graph::refreshFromRepositories,
            accountOperationGate = graph.accountOperationGate,
        ),
    )

    private val repositories = dependencies.repositories

    val state: StateFlow<MainUiState> =
        combine(repositories.auth.session, dependencies.accountDaySnapshot) { session, snapshot ->
            when {
                session == null -> {
                    MainUiState(isLoading = false, errorMessage = "Sign in to view medicines.")
                }

                snapshot == null || snapshot.ownerUid != session.uid -> {
                    MainUiState()
                }

                else -> {
                    val rows =
                        DoseRows.build(
                            snapshot.medicines.value,
                            snapshot.doses.value,
                            snapshot.logicalDay,
                            snapshot.countdowns.value,
                        )
                    MainUiState(
                        isLoading = false,
                        logicalDay = snapshot.logicalDay,
                        medicines = snapshot.medicines.value,
                        rows = rows,
                        progress = DoseRows.progress(rows),
                        isCached =
                            snapshot.medicines.fromCache ||
                                snapshot.doses.fromCache ||
                                snapshot.countdowns.fromCache,
                        hasPendingWrites =
                            snapshot.medicines.hasPendingWrites ||
                                snapshot.doses.hasPendingWrites ||
                                snapshot.countdowns.hasPendingWrites,
                        errorMessage =
                            snapshot.doses.errorMessage
                                ?: snapshot.countdowns.errorMessage
                                ?: snapshot.medicines.errorMessage,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun refreshTemporalState() {
        viewModelScope.launch { dependencies.refreshTemporalState() }
    }

    fun check(
        row: DoseRow,
        source: CheckSource = CheckSource.APP,
    ) {
        val session = repositories.auth.session.value ?: return
        val medicine = state.value.medicines.firstOrNull { it.id == row.medicineId } ?: return
        viewModelScope.launch {
            dependencies.accountOperationGate.runMutation {
                val actionDay = currentLogicalDay()
                if (actionDay != state.value.logicalDay) {
                    dependencies.refreshTemporalState()
                }
                val applied =
                    repositories.doses.check(
                        session.uid,
                        actionDay,
                        medicine,
                        row.slot,
                        source,
                    )
                if (applied && row.countdown != null) {
                    repositories.countdowns.clearForDoseCheck(
                        session.uid,
                        row.medicineId,
                        row.slot,
                        source,
                        row.countdown,
                    )
                }
                dependencies.refreshFromRepositories()
            }
        }
    }

    fun undo(row: DoseRow) {
        val session = repositories.auth.session.value ?: return
        val medicine = state.value.medicines.firstOrNull { it.id == row.medicineId } ?: return
        viewModelScope.launch {
            dependencies.accountOperationGate.runMutation {
                val actionDay = currentLogicalDay()
                if (actionDay != state.value.logicalDay) {
                    dependencies.refreshTemporalState()
                    return@runMutation
                }
                repositories.doses.undo(
                    session.uid,
                    actionDay,
                    medicine,
                    row.slot,
                    CheckSource.APP,
                )
                dependencies.refreshFromRepositories()
            }
        }
    }

    suspend fun saveMedicine(draft: MedicineDraft): ValidationResult {
        val validation = MedicineValidator.validate(draft)
        if (!validation.isValid) return validation
        val uid =
            repositories.auth.session.value
                ?.uid
                ?: return validation
        val existing = state.value.medicines.firstOrNull { it.id == validation.normalized.id }
        val activeCountdowns =
            state.value.rows
                .filter { it.medicineId == validation.normalized.id }
                .mapNotNull(DoseRow::countdown)
        dependencies.accountOperationGate.runMutation {
            repositories.medicines.save(uid, validation.normalized)
            cancelCountdownsForDisabledSlots(uid, validation.normalized, activeCountdowns)
            if (validation.normalized.restartChangedCountdowns && existing != null) {
                restartChangedCountdowns(uid, existing, validation.normalized, activeCountdowns)
            }
            dependencies.refreshFromRepositories()
        }
        return validation
    }

    fun startCountdown(
        row: DoseRow,
        source: CheckSource = CheckSource.APP,
    ) {
        val session = repositories.auth.session.value ?: return
        val medicine = state.value.medicines.firstOrNull { it.id == row.medicineId } ?: return
        val duration = medicine.countdownMinutes(row.slot) ?: return
        if (row.isTaken || row.countdown != null) return
        viewModelScope.launch {
            dependencies.accountOperationGate.runMutation {
                val actionDay = currentLogicalDay()
                repositories.countdowns.start(
                    uid = session.uid,
                    logicalDay = actionDay,
                    medicine = medicine,
                    slot = row.slot,
                    source = source,
                    actionId = UUID.randomUUID().toString(),
                    startedAt = dependencies.clock.instant(),
                    durationMinutes = duration,
                )
                dependencies.refreshFromRepositories()
            }
        }
    }

    fun cancelCountdown(row: DoseRow) {
        val session = repositories.auth.session.value ?: return
        val countdown = row.countdown ?: return
        viewModelScope.launch {
            dependencies.accountOperationGate.runMutation {
                repositories.countdowns.cancel(session.uid, countdown)
                dependencies.refreshFromRepositories()
            }
        }
    }

    fun restartCountdown(row: DoseRow) {
        val session = repositories.auth.session.value ?: return
        val countdown = row.countdown ?: return
        val medicine = state.value.medicines.firstOrNull { it.id == row.medicineId } ?: return
        val duration = medicine.countdownMinutes(row.slot) ?: return
        viewModelScope.launch {
            dependencies.accountOperationGate.runMutation {
                repositories.countdowns.restart(session.uid, countdown, duration)
                dependencies.refreshFromRepositories()
            }
        }
    }

    fun archiveMedicine(
        medicineId: String,
        archived: Boolean = true,
    ) {
        val uid =
            repositories.auth.session.value
                ?.uid
                ?: return
        viewModelScope.launch {
            dependencies.accountOperationGate.runMutation {
                state.value.rows
                    .filter { it.medicineId == medicineId }
                    .mapNotNull(DoseRow::countdown)
                    .forEach { repositories.countdowns.cancel(uid, it) }
                repositories.medicines.archive(uid, medicineId, archived)
                dependencies.refreshFromRepositories()
            }
        }
    }

    fun deleteMedicine(medicineId: String) {
        val uid =
            repositories.auth.session.value
                ?.uid
                ?: return
        viewModelScope.launch {
            dependencies.accountOperationGate.runMutation {
                state.value.rows
                    .filter { it.medicineId == medicineId }
                    .mapNotNull(DoseRow::countdown)
                    .forEach { repositories.countdowns.cancel(uid, it) }
                repositories.medicines.delete(uid, medicineId)
                dependencies.refreshFromRepositories()
            }
        }
    }

    private fun currentLogicalDay(): LocalDate {
        val settings = repositories.settings.localSettings.value
        return LogicalDayCalculator.logicalDay(
            dependencies.clock.instant(),
            ZoneId.systemDefault(),
            settings.resetMinutesAfterMidnight,
        )
    }

    private suspend fun restartChangedCountdowns(
        uid: String,
        existing: Medicine,
        draft: MedicineDraft,
        activeCountdowns: List<CountdownState>,
    ) {
        activeCountdowns.forEach { countdown ->
            if (!draft.isSlotEnabled(countdown.slot)) return@forEach
            val oldDuration = existing.countdownMinutes(countdown.slot)
            val newDuration =
                when (countdown.slot) {
                    DoseSlot.AFTERNOON -> draft.afternoonCountdownMinutes
                    DoseSlot.NIGHT -> draft.nightCountdownMinutes
                }
            if (newDuration == oldDuration) return@forEach
            if (newDuration == null) {
                repositories.countdowns.cancel(uid, countdown)
            } else {
                repositories.countdowns.restart(uid, countdown, newDuration)
            }
        }
    }

    private suspend fun cancelCountdownsForDisabledSlots(
        uid: String,
        draft: MedicineDraft,
        activeCountdowns: List<CountdownState>,
    ) {
        activeCountdowns
            .filterNot { draft.isSlotEnabled(it.slot) }
            .forEach { countdown -> repositories.countdowns.cancel(uid, countdown) }
    }

    private fun MedicineDraft.isSlotEnabled(slot: DoseSlot): Boolean =
        when (slot) {
            DoseSlot.AFTERNOON -> afternoonEnabled
            DoseSlot.NIGHT -> nightEnabled
        }
}
