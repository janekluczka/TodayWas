package pl.luczka.todaywas.ui.main

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
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.toUiState
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeOnboardingState: ObserveOnboardingStateUseCase,
    observeJournalEntries: ObserveJournalEntriesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            focus = null,
            journalEntries = emptyList(),
            addableSlots = emptyList(),
            fabActions = emptyList(),
            fabExpanded = false,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MainUiEvent>(Channel.BUFFERED)
    val events: Flow<MainUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(observeOnboardingState(), observeJournalEntries()) { onboardingState, entries ->
                onboardingState.focus to entries
            }.collect { (focus, entries) ->
                val focusUiState = focus?.toUiState()
                val journalEntries = entries.map { it.toUiState() }
                val addableSlots = journalEntries.toAddableSlots()
                _uiState.update {
                    it.copy(
                        focus = focusUiState,
                        journalEntries = journalEntries,
                        addableSlots = addableSlots,
                        fabActions = focusUiState.toFabActions(addableSlots),
                    )
                }
            }
        }
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.FabActionClicked -> {
                _uiState.update { it.copy(fabExpanded = false) }
                when (intent.action) {
                    FabActionUiState.ADD_JOURNAL_ENTRY ->
                        eventChannel.trySend(MainUiEvent.NavigateToAddEntry(_uiState.value.addableSlots))
                }
            }
            MainIntent.FabToggled ->
                _uiState.update { it.copy(fabExpanded = !it.fabExpanded) }
            is MainIntent.JournalEntryClicked ->
                eventChannel.trySend(MainUiEvent.NavigateToJournalDetail(intent.entry))
        }
    }

    private fun FocusUiState?.toFabActions(addableSlots: List<JournalDateSlotUiState>): List<FabActionUiState> {
        val journalActionAvailable =
            (this == FocusUiState.JOURNAL || this == FocusUiState.BOTH) && addableSlots.isNotEmpty()
        // Habit tracking (S-03) has no destination yet, so it never contributes an action here.
        return listOfNotNull(
            FabActionUiState.ADD_JOURNAL_ENTRY.takeIf { journalActionAvailable },
        )
    }

    private fun List<JournalEntryUiState>.toAddableSlots(): List<JournalDateSlotUiState> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val loggedDates = map { it.date }.toSet()
        return listOfNotNull(
            JournalDateSlotUiState.TODAY.takeIf { today !in loggedDates },
            JournalDateSlotUiState.YESTERDAY.takeIf { yesterday !in loggedDates },
        )
    }
}
