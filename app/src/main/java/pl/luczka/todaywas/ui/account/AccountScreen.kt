package pl.luczka.todaywas.ui.account

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
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.progress.DsLoadingIndicator
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.auth.AuthFormContent
import pl.luczka.todaywas.ui.auth.AuthFormUiState
import pl.luczka.todaywas.ui.auth.message
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi

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
                is AccountUiEvent.ShowError -> snackbarHostState.showSnackbar(errorMessages.getValue(event.error))
            }
        }
    }

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
            when (val authState = uiState.authState) {
                AuthStateUi.Loading -> DsLoadingIndicator()
                AuthStateUi.SignedOut -> AuthFormContent(
                    state = uiState.authForm,
                    onFirstNameChanged = { onIntent(AccountIntent.FirstNameChanged(it)) },
                    onLastNameChanged = { onIntent(AccountIntent.LastNameChanged(it)) },
                    onEmailChanged = { onIntent(AccountIntent.EmailChanged(it)) },
                    onPasswordChanged = { onIntent(AccountIntent.PasswordChanged(it)) },
                    onModeToggled = { onIntent(AccountIntent.ModeToggled) },
                    onSubmitClicked = { onIntent(AccountIntent.SubmitClicked) },
                    onGoogleIdTokenReceived = { onIntent(AccountIntent.GoogleIdTokenReceived(it)) },
                    onGoogleSignInFailed = { onIntent(AccountIntent.GoogleSignInFailed) },
                )
                is AuthStateUi.SignedIn -> SignedInContent(
                    email = authState.email,
                    onSignOutClicked = { onIntent(AccountIntent.SignOutClicked) },
                )
            }
        }
    }
}

@Composable
private fun SignedInContent(
    email: String?,
    onSignOutClicked: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DsText(text = email ?: stringResource(R.string.preferences_signed_in_no_email))
        DsButton(
            text = stringResource(R.string.preferences_sign_out_cta),
            onClick = onSignOutClicked,
        )
    }
}

private class AccountUiStatePreviewProvider : PreviewParameterProvider<AccountUiState> {
    override val values = sequenceOf(
        AccountUiState(authState = AuthStateUi.Loading, authForm = AuthFormUiState()),
        AccountUiState(authState = AuthStateUi.SignedOut, authForm = AuthFormUiState()),
        AccountUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            authForm = AuthFormUiState(),
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
