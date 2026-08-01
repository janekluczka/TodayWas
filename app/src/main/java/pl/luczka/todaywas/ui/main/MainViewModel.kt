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
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.toHabitUiStates
import pl.luczka.todaywas.ui.model.toUiState
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeOnboardingState: ObserveOnboardingStateUseCase,
    observeJournalEntries: ObserveJournalEntriesUseCase,
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            focus = null,
            journalEntries = emptyList(),
            habits = emptyList(),
            fabActions = emptyList(),
            fabExpanded = false,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MainUiEvent>(Channel.BUFFERED)
    val events: Flow<MainUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                observeOnboardingState(),
                observeJournalEntries(),
                observeAddableJournalDateSlots(),
                observeHabitCheckInBoard(),
            ) { onboardingState, entries, addableSlots, board ->
                CombinedMainState(
                    focus = onboardingState.focus?.toUiState(),
                    journalEntries = entries.map { it.toUiState() },
                    addableSlots = addableSlots.map { it.toUiState() },
                    habits = board.toHabitUiStates(),
                )
            }.collect { combined ->
                _uiState.update {
                    it.copy(
                        focus = combined.focus,
                        journalEntries = combined.journalEntries,
                        habits = combined.habits,
                        fabActions = combined.focus.toFabActions(combined.addableSlots, combined.habits),
                    )
                }
            }
        }
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.FabActionClicked -> onFabActionClicked(intent.action)
            MainIntent.FabToggled -> onFabToggled()
            is MainIntent.JournalEntryClicked -> onJournalEntryClicked(intent.entry)
        }
    }

    private fun onFabActionClicked(action: FabActionUiState) {
        _uiState.update { it.copy(fabExpanded = false) }
        when (action) {
            FabActionUiState.ADD_JOURNAL_ENTRY -> eventChannel.trySend(MainUiEvent.NavigateToAddEntry)
            FabActionUiState.CREATE_HABIT -> eventChannel.trySend(MainUiEvent.NavigateToCreateHabit)
            FabActionUiState.LOG_HABIT_CHECK_INS -> eventChannel.trySend(MainUiEvent.NavigateToLogHabitCheckIns)
        }
    }

    private fun onFabToggled() {
        _uiState.update { it.copy(fabExpanded = !it.fabExpanded) }
    }

    private fun onJournalEntryClicked(entry: JournalEntryUiState) {
        eventChannel.trySend(MainUiEvent.NavigateToJournalDetail(entry))
    }

    private fun FocusUiState?.toFabActions(
        addableSlots: List<JournalDateSlotUiState>,
        habits: List<HabitUiState>,
    ): List<FabActionUiState> {
        val journalActionAvailable =
            (this == FocusUiState.JOURNAL || this == FocusUiState.BOTH) && addableSlots.isNotEmpty()
        val habitFocusActive = this == FocusUiState.HABIT || this == FocusUiState.BOTH
        return listOfNotNull(
            FabActionUiState.ADD_JOURNAL_ENTRY.takeIf { journalActionAvailable },
            FabActionUiState.CREATE_HABIT.takeIf { habitFocusActive },
            FabActionUiState.LOG_HABIT_CHECK_INS.takeIf { habitFocusActive && habits.isNotEmpty() },
        )
    }

    private data class CombinedMainState(
        val focus: FocusUiState?,
        val journalEntries: List<JournalEntryUiState>,
        val addableSlots: List<JournalDateSlotUiState>,
        val habits: List<HabitUiState>,
    )
}
