package pl.luczka.todaywas.ui.main

sealed interface MainUiEvent {

    data object ExitApp : MainUiEvent
}
