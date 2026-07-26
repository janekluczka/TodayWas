package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.domain.model.Focus

sealed interface MainIntent {

    data object WelcomeContinue : MainIntent

    data class FocusOptionSelected(
        val focus: Focus,
    ) : MainIntent

    data object ConfirmSelection : MainIntent

    data object CreateAccountClicked : MainIntent

    data object AccountContinue : MainIntent

    data object FinishOnboarding : MainIntent

    data object StepBack : MainIntent

    data object SkipOnboarding : MainIntent

    data object ChangeFocusRequested : MainIntent

    data object RetrySave : MainIntent
}
