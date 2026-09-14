package pl.luczka.todaywas.ui.journal.list

import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.JournalSortUiState

sealed interface JournalListIntent {

    data class SortSelected(
        val sort: JournalSortUiState,
    ) : JournalListIntent

    data class EntryClicked(
        val entry: JournalEntryUiState,
    ) : JournalListIntent
}
