package pl.luczka.todaywas.ui.journal.detail

sealed interface JournalEntryDetailUiEvent {

    data object NavigatedBack : JournalEntryDetailUiEvent

    data object NavigateToEdit : JournalEntryDetailUiEvent
}
