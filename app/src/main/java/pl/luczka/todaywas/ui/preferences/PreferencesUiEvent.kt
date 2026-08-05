package pl.luczka.todaywas.ui.preferences

sealed interface PreferencesUiEvent {

    data object NavigateToAccount : PreferencesUiEvent
}
