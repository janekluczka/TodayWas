package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState

sealed interface MainIntent {

    data class FabActionClicked(
        val action: FabActionUiState,
    ) : MainIntent

    data object FabToggled : MainIntent

    data class JournalEntryClicked(
        val entry: JournalEntryUiState,
    ) : MainIntent

    data class HabitClicked(
        val habit: HabitUiState,
    ) : MainIntent

    data class JournalWindowSelected(
        val window: ContributionWindowUiState,
    ) : MainIntent

    data object JournalViewAllClicked : MainIntent

    data object HabitViewAllClicked : MainIntent

    data object AccountIconClicked : MainIntent

    data object AccountSheetDismissed : MainIntent

    data object SignInSignUpPromptClicked : MainIntent

    data object SignOutClicked : MainIntent

    data object SignOutConfirmed : MainIntent

    data object SignOutCancelled : MainIntent
}
