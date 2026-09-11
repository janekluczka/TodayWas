package pl.luczka.todaywas.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.progress.DsLoadingIndicator
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.auth.SignInFormContent
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormContent
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.auth.util.message
import pl.luczka.todaywas.ui.datasync.DataSyncReviewContent
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.LocalDataSummaryUi

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessages = AuthErrorUiState.entries.associateWith { it.message() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AccountUiEvent.NavigatedBack -> onBack()
                is AccountUiEvent.ShowError -> snackbarHostState.showSnackbar(
                    errorMessages.getValue(event.error),
                )
            }
        }
    }

    BackHandler(enabled = true) { viewModel.onIntent(AccountIntent.BackClicked) }

    AccountScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun AccountScreenContent(
    uiState: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.account_top_bar_title),
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(AccountIntent.BackClicked) }) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(DsSpacing.space600),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                uiState.step == AccountStep.SUCCESS -> AccountSuccessContent(onIntent)
                uiState.step == AccountStep.DATA_SYNC_REVIEW && uiState.dataSyncSummary != null ->
                    DataSyncReviewContent(
                        summary = uiState.dataSyncSummary,
                        isSyncing = uiState.isSyncing,
                        onConfirmClicked = { onIntent(AccountIntent.SyncConfirmClicked) },
                        onSkipClicked = { onIntent(AccountIntent.SyncSkipClicked) },
                    )
                else -> when (val authState = uiState.authState) {
                    AuthStateUi.Loading -> DsLoadingIndicator()
                    AuthStateUi.SignedOut -> SignedOutContent(uiState, onIntent)
                    is AuthStateUi.SignedIn -> SignedInContent(
                        email = authState.email,
                        isSigningOut = uiState.isSigningOut,
                        hasSyncedLocalData = uiState.hasSyncedLocalData,
                        onSignOutClicked = { onIntent(AccountIntent.SignOutClicked) },
                        onSyncLocalDataClicked = { onIntent(AccountIntent.SyncLocalDataClicked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedOutContent(
    uiState: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
) {
    when (uiState.step) {
        AccountStep.SIGN_IN -> SignInFormContent(
            state = uiState.signInForm,
            onEmailChanged = { onIntent(AccountIntent.SignInEmailChanged(it)) },
            onPasswordChanged = { onIntent(AccountIntent.SignInPasswordChanged(it)) },
            onSubmitClicked = { onIntent(AccountIntent.SignInSubmitClicked) },
            onGoogleIdTokenReceived = { onIntent(AccountIntent.SignInGoogleIdTokenReceived(it)) },
            onGoogleSignInFailed = { onIntent(AccountIntent.GoogleSignInFailed) },
            onSignUpLinkClicked = { onIntent(AccountIntent.SignUpLinkClicked) },
        )
        AccountStep.SIGN_UP -> SignUpFormContent(
            state = uiState.signUpForm,
            onEmailChanged = { onIntent(AccountIntent.SignUpEmailChanged(it)) },
            onPasswordChanged = { onIntent(AccountIntent.SignUpPasswordChanged(it)) },
            onRepeatPasswordChanged = { onIntent(AccountIntent.SignUpRepeatPasswordChanged(it)) },
            onSubmitClicked = { onIntent(AccountIntent.SignUpSubmitClicked) },
            onGoogleIdTokenReceived = {
                onIntent(AccountIntent.SignUpGoogleIdTokenReceived(it))
            },
            onGoogleSignInFailed = { onIntent(AccountIntent.GoogleSignInFailed) },
            onSignInLinkClicked = { onIntent(AccountIntent.SignInLinkClicked) },
        )
        AccountStep.SUCCESS -> AccountSuccessContent(onIntent)
        // Unreachable in practice: DATA_SYNC_REVIEW is only entered right after a successful
        // sign-in/sign-up, by which point authState has already become SignedIn.
        AccountStep.DATA_SYNC_REVIEW -> Unit
    }
}

@Composable
private fun AccountSuccessContent(onIntent: (AccountIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DsText(text = stringResource(R.string.account_success_title))
        DsText(text = stringResource(R.string.account_success_message))
        DsButton(
            text = stringResource(R.string.account_success_continue_cta),
            onClick = { onIntent(AccountIntent.ContinueClicked) },
        )
    }
}

@Composable
private fun SignedInContent(
    email: String?,
    isSigningOut: Boolean,
    hasSyncedLocalData: Boolean,
    onSignOutClicked: () -> Unit,
    onSyncLocalDataClicked: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DsText(text = email ?: stringResource(R.string.preferences_signed_in_no_email))
        if (!hasSyncedLocalData) {
            DsButton(
                text = stringResource(R.string.account_sync_local_data_cta),
                onClick = onSyncLocalDataClicked,
            )
        }
        DsButtonWithLoading(
            text = stringResource(R.string.preferences_sign_out_cta),
            onClick = onSignOutClicked,
            loading = isSigningOut,
        )
    }
}

private class AccountUiStatePreviewProvider : PreviewParameterProvider<AccountUiState> {
    override val values = sequenceOf(
        AccountUiState(
            authState = AuthStateUi.Loading,
            step = AccountStep.SIGN_IN,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
        ),
        AccountUiState(
            authState = AuthStateUi.SignedOut,
            step = AccountStep.SIGN_IN,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
        ),
        AccountUiState(
            authState = AuthStateUi.SignedOut,
            step = AccountStep.SIGN_UP,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
        ),
        AccountUiState(
            authState = AuthStateUi.SignedOut,
            step = AccountStep.SUCCESS,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
        ),
        AccountUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            step = AccountStep.SIGN_IN,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
        ),
        AccountUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            step = AccountStep.SUCCESS,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
        ),
        AccountUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            step = AccountStep.SIGN_IN,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
            hasSyncedLocalData = false,
        ),
        AccountUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            step = AccountStep.DATA_SYNC_REVIEW,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
            dataSyncSummary = LocalDataSummaryUi(
                journalEntryCount = 12,
                habitCount = 3,
                checkInCount = 40,
            ),
        ),
        AccountUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            step = AccountStep.DATA_SYNC_REVIEW,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
            dataSyncSummary = LocalDataSummaryUi(
                journalEntryCount = 12,
                habitCount = 3,
                checkInCount = 40,
            ),
            isSyncing = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun AccountScreenPreview(
    @PreviewParameter(AccountUiStatePreviewProvider::class) state: AccountUiState,
) {
    DsTheme {
        AccountScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
