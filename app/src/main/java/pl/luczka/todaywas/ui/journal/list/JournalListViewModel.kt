package pl.luczka.todaywas.ui.journal.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.JournalSortUiState
import javax.inject.Inject

@HiltViewModel
class JournalListViewModel @Inject constructor(
    observeJournalEntries: ObserveJournalEntriesUseCase,
) : ViewModel() {

    // The DAO already returns entries sorted date DESC (see JournalEntryDao), so NEWEST_FIRST
    // needs no re-sort — only OLDEST_FIRST does.
    private val selectedSort = MutableStateFlow(JournalSortUiState.NEWEST_FIRST)

    private val _uiState = MutableStateFlow(JournalListUiState())
    val uiState: StateFlow<JournalListUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<JournalListUiEvent>(Channel.BUFFERED)
    val events: Flow<JournalListUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(observeJournalEntries(), selectedSort) { entries, sort ->
                val uiEntries = entries.map { it.toUiState() }
                val sorted = when (sort) {
                    JournalSortUiState.NEWEST_FIRST -> uiEntries.sortedByDescending { it.date }
                    JournalSortUiState.OLDEST_FIRST -> uiEntries.sortedBy { it.date }
                }
                sorted to sort
            }.collect { (sorted, sort) ->
                _uiState.update {
                    it.copy(isLoading = false, entries = sorted, selectedSort = sort)
                }
            }
        }
    }

    fun onIntent(intent: JournalListIntent) {
        when (intent) {
            is JournalListIntent.SortSelected -> selectedSort.update { intent.sort }
            is JournalListIntent.EntryClicked -> eventChannel.trySend(
                JournalListUiEvent.NavigateToDetail(intent.entry.id),
            )
        }
    }
}
