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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.selectioncontrols.DsRadioOption
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.theme.TodayWasTheme

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingUiEvent.ExitApp -> activity?.finish()
                OnboardingUiEvent.Finished -> onFinished()
            }
        }
    }

    OnboardingScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun OnboardingScreenContent(
    uiState: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    BackHandler(enabled = true) { onIntent(OnboardingIntent.StepBack) }

    val pagerState = rememberPagerState(initialPage = uiState.step.ordinal) { OnboardingStep.entries.size }
    LaunchedEffect(uiState.step) {
        pagerState.animateScrollToPage(uiState.step.ordinal)
    }

    DsScaffold(
        topBar = { DsTopBar(title = "") },
        bottomBar = {
            OnboardingBottomBar(
                uiState = uiState,
                onIntent = onIntent,
            )
        },
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
                    .padding(24.dp),
            ) {
                when (OnboardingStep.entries[page]) {
                    OnboardingStep.WELCOME -> WelcomeStepBody()
                    OnboardingStep.FOCUS_PICK -> FocusPickStepBody(uiState, onIntent)
                    OnboardingStep.ACCOUNT_INFO -> AccountInfoStepBody(onIntent)
                    OnboardingStep.ALL_SET -> AllSetStepBody()
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    uiState: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
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
            loading = uiState.step == OnboardingStep.FOCUS_PICK && uiState.isSaving,
        )
    }
}

@Composable
private fun nextButtonLabel(uiState: OnboardingUiState): String = when (uiState.step) {
    OnboardingStep.WELCOME -> stringResource(R.string.onboarding_welcome_cta)
    OnboardingStep.FOCUS_PICK ->
        if (uiState.saveError) {
            stringResource(R.string.onboarding_focus_pick_retry)
        } else {
            stringResource(R.string.onboarding_focus_pick_confirm)
        }
    OnboardingStep.ACCOUNT_INFO -> stringResource(R.string.onboarding_account_continue_cta)
    OnboardingStep.ALL_SET -> stringResource(R.string.onboarding_all_set_cta)
}

private fun nextButtonEnabled(uiState: OnboardingUiState): Boolean = when (uiState.step) {
    OnboardingStep.WELCOME, OnboardingStep.ACCOUNT_INFO, OnboardingStep.ALL_SET -> true
    OnboardingStep.FOCUS_PICK -> !uiState.isSaving && (uiState.saveError || uiState.selectedFocus != null)
}

@Composable
private fun WelcomeStepBody() {
    Column {
        DsText(text = stringResource(R.string.onboarding_welcome_title))
        DsText(text = stringResource(R.string.onboarding_welcome_description))
    }
}

@Composable
private fun FocusPickStepBody(
    uiState: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DsText(text = stringResource(R.string.onboarding_focus_pick_title))
        for (focus in FocusUiState.entries) {
            DsRadioOption(
                text = focus.label(),
                selected = uiState.selectedFocus == focus,
                onClick = { onIntent(OnboardingIntent.FocusOptionSelected(focus)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (uiState.saveError) {
            DsText(text = stringResource(R.string.onboarding_focus_pick_error))
        }
    }
}

@Composable
private fun AccountInfoStepBody(onIntent: (OnboardingIntent) -> Unit) {
    Column {
        DsText(text = stringResource(R.string.onboarding_account_description))
        DsTextButton(
            text = stringResource(R.string.onboarding_account_create_cta),
            onClick = { onIntent(OnboardingIntent.CreateAccountClicked) },
        )
    }
}

@Composable
private fun AllSetStepBody() {
    DsText(text = stringResource(R.string.onboarding_all_set_title))
}

@Composable
private fun FocusUiState.label(): String = when (this) {
    FocusUiState.JOURNAL -> stringResource(R.string.focus_journal)
    FocusUiState.HABIT -> stringResource(R.string.focus_habit)
    FocusUiState.BOTH -> stringResource(R.string.focus_both)
}

private class OnboardingScreenPreviewStateProvider : PreviewParameterProvider<OnboardingUiState> {
    override val values = sequenceOf(
        previewState(step = OnboardingStep.WELCOME),
        previewState(step = OnboardingStep.FOCUS_PICK),
        previewState(
            step = OnboardingStep.FOCUS_PICK,
            selectedFocus = FocusUiState.JOURNAL,
        ),
        previewState(
            step = OnboardingStep.FOCUS_PICK,
            selectedFocus = FocusUiState.HABIT,
            isSaving = true,
        ),
        previewState(
            step = OnboardingStep.FOCUS_PICK,
            selectedFocus = FocusUiState.BOTH,
            saveError = true,
        ),
        previewState(
            step = OnboardingStep.ACCOUNT_INFO,
            confirmedFocus = FocusUiState.JOURNAL,
        ),
        previewState(
            step = OnboardingStep.ALL_SET,
            confirmedFocus = FocusUiState.JOURNAL,
        ),
    )
}

private fun previewState(
    step: OnboardingStep,
    selectedFocus: FocusUiState? = null,
    confirmedFocus: FocusUiState? = null,
    isSaving: Boolean = false,
    saveError: Boolean = false,
) = OnboardingUiState(
    step = step,
    selectedFocus = selectedFocus,
    confirmedFocus = confirmedFocus,
    isSaving = isSaving,
    saveError = saveError,
)

@PreviewLightDark
@Composable
private fun OnboardingScreenPreview(
    @PreviewParameter(OnboardingScreenPreviewStateProvider::class) state: OnboardingUiState,
) {
    TodayWasTheme {
        OnboardingScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
