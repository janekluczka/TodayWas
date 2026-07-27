package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.model.JournalEntry

data class MainUiState(
    val focus: Focus?,
    val journalEntries: List<JournalEntry>,
    val addableSlots: List<JournalDateSlot>,
)
