package pl.luczka.todaywas.ui.journal.list

import pl.luczka.todaywas.core.ui.model.JournalSortUiState

sealed interface JournalListIntent {

    data class SortSelected(
        val sort: JournalSortUiState,
    ) : JournalListIntent
}
