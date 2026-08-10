package io.github.ffelixq.medswidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.domain.AdherenceCalculator
import io.github.ffelixq.medswidget.domain.AdherenceSummary
import io.github.ffelixq.medswidget.domain.HistoryAssembler
import io.github.ffelixq.medswidget.domain.HistoryEntry
import io.github.ffelixq.medswidget.domain.LogicalDayCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class HistoryUiState(
    val isLoading: Boolean = true,
    val logicalDay: LocalDate = LocalDate.now(),
    val entries: List<HistoryEntry> = emptyList(),
    val sevenDay: AdherenceSummary = AdherenceSummary(0, 0, 0, 0),
    val thirtyDay: AdherenceSummary = AdherenceSummary(0, 0, 0, 0),
    val ninetyDay: AdherenceSummary = AdherenceSummary(0, 0, 0, 0),
    val isCached: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    graph: AppGraph,
) : ViewModel() {
    val state: StateFlow<HistoryUiState> =
        graph.repositories.auth.session
            .flatMapLatest { session ->
                if (session == null) {
                    flowOf(HistoryUiState(isLoading = false))
                } else {
                    combine(
                        graph.repositories.doses.observeHistory(session.uid),
                        graph.repositories.medicines.observeAll(session.uid),
                        graph.repositories.settings.localSettings,
                    ) { history, medicines, settings ->
                        val today =
                            LogicalDayCalculator.logicalDay(
                                graph.clock.instant(),
                                ZoneId.systemDefault(),
                                settings.resetMinutesAfterMidnight,
                            )
                        HistoryUiState(
                            isLoading = false,
                            logicalDay = today,
                            entries = HistoryAssembler.assemble(history.value),
                            sevenDay = AdherenceCalculator.summarize(history.value, medicines.value, today, 7),
                            thirtyDay = AdherenceCalculator.summarize(history.value, medicines.value, today, 30),
                            ninetyDay = AdherenceCalculator.summarize(history.value, medicines.value, today, 90),
                            isCached = history.fromCache || medicines.fromCache,
                            errorMessage = history.errorMessage ?: medicines.errorMessage,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
