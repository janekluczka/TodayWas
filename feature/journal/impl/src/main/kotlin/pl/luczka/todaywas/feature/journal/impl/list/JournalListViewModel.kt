package pl.luczka.todaywas.feature.journal.impl.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pl.luczka.todaywas.core.domain.usecase.ObserveJournalContributionUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.domain.entry.model.ContributionWindow
import pl.luczka.todaywas.feature.journal.impl.model.JournalSortUiState

@HiltViewModel
internal class JournalListViewModel @Inject constructor(
    observeJournalEntries: ObserveJournalEntriesUseCase,
    observeJournalContribution: ObserveJournalContributionUseCase,
    private val clock: Clock,
) : ViewModel() {

    // The DAO already returns entries sorted date DESC (see JournalEntryDao), so NEWEST_FIRST
    // needs no re-sort — only OLDEST_FIRST does.
    private val selectedSort = MutableStateFlow(JournalSortUiState.NEWEST_FIRST)

    val uiState: StateFlow<JournalListUiState> = combine(
        observeJournalEntries(),
        selectedSort,
        observeJournalContribution(flowOf(ContributionWindow.RollingTwelveMonths)),
    ) { entries, sort, summary ->
        val uiEntries = entries.map { it.toUiState() }
        JournalListUiState(
            isLoading = false,
            entries = when (sort) {
                JournalSortUiState.NEWEST_FIRST -> uiEntries.sortedByDescending { it.date }
                JournalSortUiState.OLDEST_FIRST -> uiEntries.sortedBy { it.date }
            },
            selectedSort = sort,
            contributionGrid = summary.grid.toUiState(clock.instant()),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalListUiState(),
    )

    fun onIntent(intent: JournalListIntent) {
        when (intent) {
            is JournalListIntent.SortSelected -> onSortSelected(intent.sort)
        }
    }

    private fun onSortSelected(sort: JournalSortUiState) {
        selectedSort.update { sort }
    }
}
