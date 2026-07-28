package pl.luczka.todaywas.ui.main

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Immutable
data class MainUiState(
    val focus: FocusUiState?,
    val journalEntries: List<JournalEntryUiState>,
    val addableSlots: List<JournalDateSlotUiState>,
    val fabActions: List<FabActionUiState>,
    val fabExpanded: Boolean,
)
