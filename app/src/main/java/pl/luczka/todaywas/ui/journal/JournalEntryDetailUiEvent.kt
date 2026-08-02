package pl.luczka.todaywas.ui.journal

sealed interface JournalEntryDetailUiEvent {

    data object NavigatedBack : JournalEntryDetailUiEvent
}
