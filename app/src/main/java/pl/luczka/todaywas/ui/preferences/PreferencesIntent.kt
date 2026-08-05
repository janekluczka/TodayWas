package pl.luczka.todaywas.ui.preferences

sealed interface PreferencesIntent {

    data object AccountCardClicked : PreferencesIntent
}
