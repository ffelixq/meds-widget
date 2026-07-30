package io.github.ffelixq.medswidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.domain.HistoryAssembler
import io.github.ffelixq.medswidget.domain.HistoryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
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
                    graph.repositories.doses.observeHistory(session.uid).map { envelope ->
                        HistoryUiState(
                            isLoading = false,
                            entries = HistoryAssembler.assemble(envelope.value),
                            isCached = envelope.fromCache,
                            errorMessage = envelope.errorMessage,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
