package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState

sealed interface MainUiEvent {

    data class NavigateToAddEntry(
        val availableSlots: List<JournalDateSlotUiState>,
    ) : MainUiEvent

    data class NavigateToJournalDetail(
        val entry: JournalEntryUiState,
    ) : MainUiEvent
}
