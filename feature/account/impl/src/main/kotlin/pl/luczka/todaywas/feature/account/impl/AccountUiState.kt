package pl.luczka.todaywas.feature.account.impl

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.core.ui.auth.SignInFormUiState
import pl.luczka.todaywas.core.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.core.ui.model.AuthErrorUiState
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.core.ui.model.LocalDataSummaryUi

@Immutable
internal data class AccountUiState(
    val authState: AuthStateUi,
    val step: AccountStep,
    val signInForm: SignInFormUiState,
    val signUpForm: SignUpFormUiState,
    val isSigningOut: Boolean = false,
    val dataSyncSummary: LocalDataSummaryUi? = null,
    val isSyncing: Boolean = false,
    val hasSyncedLocalData: Boolean = false,
    val pendingNavigation: AccountNavigation? = null,
    // An error to show once in a snackbar; cleared by AccountIntent.UserMessageShown.
    val userMessage: AuthErrorUiState? = null,
)
