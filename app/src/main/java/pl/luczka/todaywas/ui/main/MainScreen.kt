package pl.luczka.todaywas.ui.main

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasIcon
import pl.luczka.todaywas.core.designsystem.components.TodayWasIconButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.ui.theme.TodayWasTheme

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                MainUiEvent.ExitApp -> activity.finish()
            }
        }
    }

    MainScreenContent(uiState = uiState, onIntent = viewModel::onIntent)
}

@Composable
private fun MainScreenContent(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
) {
    TodayWasScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TodayWasTopBar(
                title = stringResource(R.string.main_top_bar_title),
                actions = {
                    if (uiState.currentFocus != null) {
                        TodayWasIconButton(onClick = { onIntent(MainIntent.ChangeFocusRequested) }) {
                            TodayWasIcon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.main_change_focus_content_description),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            TodayWasText(text = stringResource(R.string.main_empty_state))
        }
    }

    if (uiState.showOnboardingDialog) {
        OnboardingDialog(
            state = uiState.onboardingDialogState,
            onDismiss = { onIntent(MainIntent.DialogDismissed) },
            onStepBack = { onIntent(MainIntent.StepBack) },
            onSkip = { onIntent(MainIntent.SkipOnboarding) },
            onWelcomeContinue = { onIntent(MainIntent.WelcomeContinue) },
            onFocusOptionSelected = { onIntent(MainIntent.FocusOptionSelected(it)) },
            onConfirmSelection = { onIntent(MainIntent.ConfirmSelection) },
            onRetrySave = { onIntent(MainIntent.RetrySave) },
            onCreateAccountClicked = { onIntent(MainIntent.CreateAccountClicked) },
            onAccountContinue = { onIntent(MainIntent.AccountContinue) },
            onFinishOnboarding = { onIntent(MainIntent.FinishOnboarding) },
        )
    }
}

@PreviewLightDark
@Composable
private fun MainScreenOnboardingCompletePreview() {
    TodayWasTheme {
        MainScreenContent(
            uiState =
                MainUiState(
                    currentFocus = Focus.BOTH,
                    showOnboardingDialog = false,
                    onboardingDialogState =
                        OnboardingDialogState(
                            onboardingMode = OnboardingMode.MANDATORY,
                            onboardingStep = OnboardingStep.WELCOME,
                            selectedFocusInDialog = null,
                            isSaving = false,
                            saveError = false,
                        ),
                ),
            onIntent = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MainScreenOnboardingPendingPreview() {
    TodayWasTheme {
        MainScreenContent(
            uiState =
                MainUiState(
                    currentFocus = null,
                    showOnboardingDialog = true,
                    onboardingDialogState =
                        OnboardingDialogState(
                            onboardingMode = OnboardingMode.MANDATORY,
                            onboardingStep = OnboardingStep.WELCOME,
                            selectedFocusInDialog = null,
                            isSaving = false,
                            saveError = false,
                        ),
                ),
            onIntent = {},
        )
    }
}
