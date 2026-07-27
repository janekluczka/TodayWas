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
import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeOnboardingState: ObserveOnboardingStateUseCase,
    observeJournalEntries: ObserveJournalEntriesUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            MainUiState(focus = null, journalEntries = emptyList(), addableSlots = emptyList()),
        )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MainUiEvent>(Channel.BUFFERED)
    val events: Flow<MainUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(observeOnboardingState(), observeJournalEntries()) { onboardingState, entries ->
                onboardingState.focus to entries
            }.collect { (focus, entries) ->
                _uiState.update {
                    it.copy(focus = focus, journalEntries = entries, addableSlots = entries.toAddableSlots())
                }
            }
        }
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            MainIntent.AddEntryClicked ->
                eventChannel.trySend(MainUiEvent.NavigateToAddEntry(_uiState.value.addableSlots))
            is MainIntent.JournalEntryClicked ->
                eventChannel.trySend(MainUiEvent.NavigateToJournalDetail(intent.entry))
        }
    }

    private fun List<JournalEntry>.toAddableSlots(): List<JournalDateSlot> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val loggedDates = map { it.date }.toSet()
        return listOfNotNull(
            JournalDateSlot.TODAY.takeIf { today !in loggedDates },
            JournalDateSlot.YESTERDAY.takeIf { yesterday !in loggedDates },
        )
    }
}
