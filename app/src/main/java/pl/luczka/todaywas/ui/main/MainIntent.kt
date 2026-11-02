package pl.luczka.todaywas.ui.main

// Navigation-only taps (opening a detail, a list, the add/create flows) don't go through the
// ViewModel: the screen calls its navigation lambdas directly. Intents here change state.
sealed interface MainIntent {

    // A FAB menu action was tapped; the ViewModel collapses the menu, the screen navigates.
    data object FabActionClicked : MainIntent

    data object FabToggled : MainIntent

    data object AccountIconClicked : MainIntent

    data object AccountSheetDismissed : MainIntent

    // The sheet's sign-in/sign-up prompt; the ViewModel closes the sheet, the screen navigates.
    data object SignInSignUpPromptClicked : MainIntent

    data object SignOutClicked : MainIntent

    data object SignOutConfirmed : MainIntent

    data object SignOutCancelled : MainIntent

    data object UserMessageShown : MainIntent
}
