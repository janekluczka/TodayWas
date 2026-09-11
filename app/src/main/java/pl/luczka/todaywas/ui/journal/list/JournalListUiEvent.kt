package pl.luczka.todaywas.ui.journal.list

sealed interface JournalListUiEvent {

    data class NavigateToDetail(
        val id: String,
    ) : JournalListUiEvent
}
