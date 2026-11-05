package pl.luczka.todaywas.feature.journal.impl.list

import pl.luczka.todaywas.feature.journal.impl.model.JournalSortUiState

internal sealed interface JournalListIntent {

    data class SortSelected(
        val sort: JournalSortUiState,
    ) : JournalListIntent
}
