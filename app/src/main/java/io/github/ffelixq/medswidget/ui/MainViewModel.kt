package io.github.ffelixq.medswidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ffelixq.medswidget.AccountDaySnapshot
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.data.RepositoryBundle
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CompletionProgress
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
                        )
                    MainUiState(
                        isLoading = false,
                        logicalDay = snapshot.logicalDay,
                        medicines = snapshot.medicines.value,
                        rows = rows,
                        progress = DoseRows.progress(rows),
                        isCached = snapshot.medicines.fromCache || snapshot.doses.fromCache,
                        hasPendingWrites =
                            snapshot.medicines.hasPendingWrites ||
                                snapshot.doses.hasPendingWrites,
                        errorMessage =
                            snapshot.doses.errorMessage
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
                repositories.doses.check(
                    session.uid,
                    actionDay,
                    medicine,
                    row.slot,
                    source,
                )
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
        dependencies.accountOperationGate.runMutation {
            repositories.medicines.save(uid, validation.normalized)
            dependencies.refreshFromRepositories()
        }
        return validation
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
}
