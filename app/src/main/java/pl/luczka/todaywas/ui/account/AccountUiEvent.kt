package pl.luczka.todaywas.ui.account

import pl.luczka.todaywas.ui.model.AuthErrorUiState

sealed interface AccountUiEvent {

    data object NavigatedBack : AccountUiEvent

    data class ShowError(
        val error: AuthErrorUiState,
    ) : AccountUiEvent
}
