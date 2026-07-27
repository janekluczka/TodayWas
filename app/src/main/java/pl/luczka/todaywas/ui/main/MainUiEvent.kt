package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.model.JournalEntry

sealed interface MainUiEvent {

    data class NavigateToAddEntry(
        val availableSlots: List<JournalDateSlot>,
    ) : MainUiEvent

    data class NavigateToJournalDetail(
        val entry: JournalEntry,
    ) : MainUiEvent
}
