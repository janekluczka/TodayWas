package pl.luczka.todaywas.ui.onboarding.accountsetup

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
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.auth.SignInFormContent
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormContent
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.auth.util.message
import pl.luczka.todaywas.ui.datasync.DataSyncReviewContent
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.LocalDataSummaryUi
import pl.luczka.todaywas.ui.onboarding.AccountSubStep
import pl.luczka.todaywas.ui.onboarding.AllSetReason

@Composable
fun OnboardingAccountSetupScreen(
    onBack: () -> Unit,
    onFinished: (AllSetReason) -> Unit,
    viewModel: OnboardingAccountSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessages = AuthErrorUiState.entries.associateWith { it.message() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingAccountSetupUiEvent.NavigateBack -> onBack()
                is OnboardingAccountSetupUiEvent.Finished -> onFinished(event.reason)
                is OnboardingAccountSetupUiEvent.ShowError -> snackbarHostState.showSnackbar(
                    errorMessages.getValue(event.error),
                )
            }
        }
    }

    OnboardingAccountSetupScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
    )
}

private val AUTH_FORM_SUBSTEPS = setOf(AccountSubStep.SIGN_IN, AccountSubStep.SIGN_UP)

// The extra top padding (on top of the screen's own space600) clears the floating back button
// below, which sits outside this padding at the true top-left.
private val AUTH_FORM_TOP_CLEARANCE = DsSpacing.space1000

@Composable
private fun OnboardingAccountSetupScreenContent(
    uiState: OnboardingAccountSetupUiState,
    onIntent: (OnboardingAccountSetupIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    DsScaffold(
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DsSpacing.space600),
            ) {
                when (uiState.accountSubStep) {
                    AccountSubStep.SIGN_IN -> SignInFormContent(
                        state = uiState.signInForm,
                        onEmailChanged = {
                            onIntent(OnboardingAccountSetupIntent.SignInEmailChanged(it))
                        },
                        onPasswordChanged = {
                            onIntent(OnboardingAccountSetupIntent.SignInPasswordChanged(it))
                        },
                        onSubmitClicked = {
                            onIntent(OnboardingAccountSetupIntent.SignInSubmitClicked)
                        },
                        onGoogleIdTokenReceived = {
                            onIntent(OnboardingAccountSetupIntent.SignInGoogleIdTokenReceived(it))
                        },
                        onGoogleSignInFailed = {
                            onIntent(OnboardingAccountSetupIntent.GoogleSignInFailed)
                        },
                        onSignUpLinkClicked = {
                            onIntent(OnboardingAccountSetupIntent.SignUpLinkClicked)
                        },
                        modifier = Modifier.padding(top = AUTH_FORM_TOP_CLEARANCE),
                    )
                    AccountSubStep.SIGN_UP -> SignUpFormContent(
                        state = uiState.signUpForm,
                        onEmailChanged = {
                            onIntent(OnboardingAccountSetupIntent.SignUpEmailChanged(it))
                        },
                        onPasswordChanged = {
                            onIntent(OnboardingAccountSetupIntent.SignUpPasswordChanged(it))
                        },
                        onRepeatPasswordChanged = {
                            onIntent(OnboardingAccountSetupIntent.SignUpRepeatPasswordChanged(it))
                        },
                        onSubmitClicked = {
                            onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)
                        },
                        onGoogleIdTokenReceived = {
                            onIntent(OnboardingAccountSetupIntent.SignUpGoogleIdTokenReceived(it))
                        },
                        onGoogleSignInFailed = {
                            onIntent(OnboardingAccountSetupIntent.GoogleSignInFailed)
                        },
                        onSignInLinkClicked = {
                            onIntent(OnboardingAccountSetupIntent.SignInLinkClicked)
                        },
                        modifier = Modifier.padding(top = AUTH_FORM_TOP_CLEARANCE),
                    )
                    AccountSubStep.DATA_SYNC_REVIEW -> {
                        val summary = uiState.dataSyncSummary
                        if (summary != null) {
                            DataSyncReviewContent(
                                summary = summary,
                                isSyncing = uiState.isSyncing,
                                onConfirmClicked = {
                                    onIntent(OnboardingAccountSetupIntent.SyncConfirmClicked)
                                },
                                onSkipClicked = {
                                    onIntent(OnboardingAccountSetupIntent.SyncSkipClicked)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            // Floats where a topbar's navigation icon normally sits, without the AppBar
            // surface/elevation — a sibling overlay rather than nested in the padded content,
            // so it sits flush at the true top-left instead of indented by the content's own
            // space600 padding.
            if (uiState.accountSubStep in AUTH_FORM_SUBSTEPS) {
                DsIconButton(
                    onClick = { onIntent(OnboardingAccountSetupIntent.StepBack) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(DsSpacing.space300),
                ) {
                    DsIcon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            }
        }
    }
}

private class OnboardingAccountSetupUiStatePreviewProvider :
    PreviewParameterProvider<OnboardingAccountSetupUiState> {
    override val values = sequenceOf(
        OnboardingAccountSetupUiState(
            accountSubStep = AccountSubStep.SIGN_IN,
            signInForm = SignInFormUiState(),
        ),
        OnboardingAccountSetupUiState(
            accountSubStep = AccountSubStep.SIGN_UP,
            signUpForm = SignUpFormUiState(),
        ),
        OnboardingAccountSetupUiState(
            accountSubStep = AccountSubStep.DATA_SYNC_REVIEW,
            dataSyncSummary = LocalDataSummaryUi(
                journalEntryCount = 12,
                habitCount = 3,
                checkInCount = 40,
            ),
        ),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingAccountSetupScreenPreview(
    @PreviewParameter(OnboardingAccountSetupUiStatePreviewProvider::class)
    state: OnboardingAccountSetupUiState,
) {
    DsTheme {
        OnboardingAccountSetupScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
