package pl.luczka.todaywas.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.auth.SignInFormContent
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormContent
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.auth.message
import pl.luczka.todaywas.ui.datasync.DataSyncReviewContent
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.LocalDataSummaryUi

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessages = AuthErrorUiState.entries.associateWith { it.message() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingUiEvent.ExitApp -> activity?.finish()
                OnboardingUiEvent.Finished -> onFinished()
                is OnboardingUiEvent.ShowError -> snackbarHostState.showSnackbar(errorMessages.getValue(event.error))
            }
        }
    }

    OnboardingScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun OnboardingScreenContent(
    uiState: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = true) { onIntent(OnboardingIntent.StepBack) }

    val pagerState = rememberPagerState(initialPage = uiState.step.ordinal) { OnboardingStep.entries.size }
    LaunchedEffect(uiState.step) {
        pagerState.animateScrollToPage(uiState.step.ordinal)
    }
    LaunchedEffect(uiState.step) {
        if (uiState.step == OnboardingStep.ALL_SET) {
            delay(ALL_SET_AUTO_ADVANCE_DELAY_MS)
            onIntent(OnboardingIntent.NextClicked)
        }
    }

    DsScaffold(
        topBar = { DsTopBar(title = "") },
        bottomBar = {
            OnboardingBottomBar(
                uiState = uiState,
                onIntent = onIntent,
            )
        },
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DsSpacing.space600),
            ) {
                when (OnboardingStep.entries[page]) {
                    OnboardingStep.WELCOME -> WelcomeStepBody()
                    OnboardingStep.ACCOUNT_INFO -> AccountInfoStepBody(uiState, onIntent)
                    OnboardingStep.ALL_SET -> AllSetStepBody(uiState)
                }
            }
        }
    }
}

private const val ALL_SET_AUTO_ADVANCE_DELAY_MS = 5_000L

@Composable
private fun OnboardingBottomBar(
    uiState: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(DsSpacing.space600),
    ) {
        DsTextButton(
            text = stringResource(R.string.onboarding_skip),
            onClick = { onIntent(OnboardingIntent.SkipClicked) },
            enabled = !uiState.isSaving,
        )
        DsButtonWithLoading(
            text = nextButtonLabel(uiState),
            onClick = { onIntent(OnboardingIntent.NextClicked) },
            enabled = nextButtonEnabled(uiState),
            loading = uiState.step == OnboardingStep.ACCOUNT_INFO && uiState.isSaving,
        )
    }
}

@Composable
private fun nextButtonLabel(uiState: OnboardingUiState): String = when (uiState.step) {
    OnboardingStep.WELCOME -> stringResource(R.string.onboarding_welcome_cta)
    OnboardingStep.ACCOUNT_INFO ->
        if (uiState.saveError) {
            stringResource(R.string.onboarding_account_retry)
        } else {
            stringResource(R.string.onboarding_account_continue_cta)
        }
    OnboardingStep.ALL_SET -> stringResource(R.string.onboarding_all_set_cta)
}

private fun nextButtonEnabled(uiState: OnboardingUiState): Boolean = when (uiState.step) {
    OnboardingStep.WELCOME, OnboardingStep.ALL_SET -> true
    OnboardingStep.ACCOUNT_INFO -> !uiState.isSaving
}

@Composable
private fun WelcomeStepBody() {
    Column {
        DsText(text = stringResource(R.string.onboarding_welcome_title))
        DsText(text = stringResource(R.string.onboarding_welcome_description))
    }
}

@Composable
private fun AccountInfoStepBody(
    uiState: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    val authState = uiState.authState
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            uiState.accountSubStep == AccountSubStep.DATA_SYNC_REVIEW && uiState.dataSyncSummary != null ->
                DataSyncReviewContent(
                    summary = uiState.dataSyncSummary,
                    isSyncing = uiState.isSyncing,
                    onConfirmClicked = { onIntent(OnboardingIntent.SyncConfirmClicked) },
                    onSkipClicked = { onIntent(OnboardingIntent.SyncSkipClicked) },
                )
            authState is AuthStateUi.SignedIn -> AccountSignedInBody(email = authState.email)
            uiState.accountSubStep == AccountSubStep.CHOICE -> AccountChoiceBody(onIntent)
            uiState.accountSubStep == AccountSubStep.SIGN_IN -> AccountSignInBody(uiState.signInForm, onIntent)
            else -> AccountSignUpBody(uiState.signUpForm, onIntent)
        }
        if (uiState.saveError) {
            DsText(text = stringResource(R.string.onboarding_account_error))
        }
    }
}

@Composable
private fun AccountSignedInBody(email: String?) {
    DsText(
        text = stringResource(
            R.string.onboarding_account_signed_in_format,
            email ?: stringResource(R.string.preferences_signed_in_no_email),
        ),
    )
}

@Composable
private fun AccountChoiceBody(onIntent: (OnboardingIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.space400)) {
        DsText(text = stringResource(R.string.onboarding_account_description))
        DsTextButton(
            text = stringResource(R.string.onboarding_account_continue_without_account_cta),
            onClick = { onIntent(OnboardingIntent.ContinueWithoutAccountClicked) },
        )
        DsTextButton(
            text = stringResource(R.string.onboarding_account_signin_signup_cta),
            onClick = { onIntent(OnboardingIntent.SignInSignUpClicked) },
        )
    }
}

@Composable
private fun AccountSignInBody(
    signInForm: SignInFormUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    SignInFormContent(
        state = signInForm,
        onEmailChanged = { onIntent(OnboardingIntent.SignInEmailChanged(it)) },
        onPasswordChanged = { onIntent(OnboardingIntent.SignInPasswordChanged(it)) },
        onSubmitClicked = { onIntent(OnboardingIntent.SignInSubmitClicked) },
        onGoogleIdTokenReceived = { onIntent(OnboardingIntent.SignInGoogleIdTokenReceived(it)) },
        onGoogleSignInFailed = { onIntent(OnboardingIntent.GoogleSignInFailed) },
        onSignUpLinkClicked = { onIntent(OnboardingIntent.SignUpLinkClicked) },
    )
}

@Composable
private fun AccountSignUpBody(
    signUpForm: SignUpFormUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    SignUpFormContent(
        state = signUpForm,
        onEmailChanged = { onIntent(OnboardingIntent.SignUpEmailChanged(it)) },
        onPasswordChanged = { onIntent(OnboardingIntent.SignUpPasswordChanged(it)) },
        onRepeatPasswordChanged = { onIntent(OnboardingIntent.SignUpRepeatPasswordChanged(it)) },
        onSubmitClicked = { onIntent(OnboardingIntent.SignUpSubmitClicked) },
    )
}

@Composable
private fun AllSetStepBody(uiState: OnboardingUiState) {
    val email = (uiState.authState as? AuthStateUi.SignedIn)?.email
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.space400)) {
        DsText(text = stringResource(R.string.onboarding_all_set_title))
        DsText(text = allSetMessage(uiState.allSetReason, email))
    }
}

@Composable
private fun allSetMessage(
    reason: AllSetReason,
    email: String?,
): String = when (reason) {
    AllSetReason.NO_ACCOUNT -> stringResource(R.string.onboarding_all_set_no_account_message)
    AllSetReason.SIGNED_IN -> stringResource(
        R.string.onboarding_all_set_signed_in_format,
        email ?: stringResource(R.string.preferences_signed_in_no_email),
    )
    AllSetReason.ACCOUNT_CREATED -> stringResource(R.string.onboarding_all_set_account_created_message)
}

private class OnboardingScreenPreviewStateProvider : PreviewParameterProvider<OnboardingUiState> {
    override val values = sequenceOf(
        previewState(step = OnboardingStep.WELCOME),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            accountSubStep = AccountSubStep.CHOICE,
        ),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            accountSubStep = AccountSubStep.CHOICE,
            isSaving = true,
        ),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            accountSubStep = AccountSubStep.CHOICE,
            saveError = true,
        ),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            accountSubStep = AccountSubStep.SIGN_IN,
            signInForm = SignInFormUiState(),
        ),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            accountSubStep = AccountSubStep.SIGN_UP,
            signUpForm = SignUpFormUiState(),
        ),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
        ),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            accountSubStep = AccountSubStep.DATA_SYNC_REVIEW,
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            dataSyncSummary = LocalDataSummaryUi(journalEntryCount = 12, habitCount = 3, checkInCount = 40),
        ),
        previewState(
            step = OnboardingStep.ALL_SET,
            allSetReason = AllSetReason.NO_ACCOUNT,
        ),
        previewState(
            step = OnboardingStep.ALL_SET,
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            allSetReason = AllSetReason.SIGNED_IN,
        ),
        previewState(
            step = OnboardingStep.ALL_SET,
            allSetReason = AllSetReason.ACCOUNT_CREATED,
        ),
    )
}

private fun previewState(
    step: OnboardingStep,
    isSaving: Boolean = false,
    saveError: Boolean = false,
    accountSubStep: AccountSubStep = AccountSubStep.CHOICE,
    authState: AuthStateUi = AuthStateUi.SignedOut,
    signInForm: SignInFormUiState = SignInFormUiState(),
    signUpForm: SignUpFormUiState = SignUpFormUiState(),
    allSetReason: AllSetReason = AllSetReason.NO_ACCOUNT,
    dataSyncSummary: LocalDataSummaryUi? = null,
) = OnboardingUiState(
    step = step,
    isSaving = isSaving,
    saveError = saveError,
    accountSubStep = accountSubStep,
    authState = authState,
    signInForm = signInForm,
    signUpForm = signUpForm,
    allSetReason = allSetReason,
    dataSyncSummary = dataSyncSummary,
)

@PreviewLightDark
@Composable
private fun OnboardingScreenPreview(
    @PreviewParameter(OnboardingScreenPreviewStateProvider::class) state: OnboardingUiState,
) {
    DsTheme {
        OnboardingScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
