package pl.luczka.todaywas.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasDialog
import pl.luczka.todaywas.core.designsystem.components.TodayWasLoadingIndicator
import pl.luczka.todaywas.core.designsystem.components.TodayWasRadioOption
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTextButton
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.ui.theme.TodayWasTheme

@Composable
fun OnboardingDialog(
    state: OnboardingDialogState,
    onDismiss: () -> Unit,
    onStepBack: () -> Unit,
    onSkip: () -> Unit,
    onWelcomeContinue: () -> Unit,
    onFocusOptionSelected: (Focus) -> Unit,
    onConfirmSelection: () -> Unit,
    onRetrySave: () -> Unit,
    onCreateAccountClicked: () -> Unit,
    onAccountContinue: () -> Unit,
    onFinishOnboarding: () -> Unit,
) {
    val isMandatory = state.onboardingMode == OnboardingMode.MANDATORY

    TodayWasDialog(
        onDismissRequest = { if (!isMandatory) onDismiss() },
        dismissOnBackPress = !isMandatory,
        dismissOnClickOutside = !isMandatory,
    ) {
        if (isMandatory) {
            // Single callback for all four steps — the ViewModel's onStepBack() branches on
            // onboardingStep internally (WELCOME emits an ExitApp event instead of changing the step).
            BackHandler(enabled = true) { onStepBack() }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            if (isMandatory) {
                TodayWasTextButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = onSkip,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Column(modifier = Modifier.padding(top = 40.dp)) {
                when (state.onboardingStep) {
                    OnboardingStep.WELCOME -> WelcomeStepContent(onWelcomeContinue)
                    OnboardingStep.FOCUS_PICK ->
                        FocusPickStepContent(state, onFocusOptionSelected, onConfirmSelection, onRetrySave)
                    OnboardingStep.ACCOUNT_INFO -> AccountInfoStepContent(onCreateAccountClicked, onAccountContinue)
                    OnboardingStep.ALL_SET -> AllSetStepContent(onFinishOnboarding)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(onWelcomeContinue: () -> Unit) {
    TodayWasText(text = stringResource(R.string.onboarding_welcome_title))
    TodayWasText(text = stringResource(R.string.onboarding_welcome_description))
    TodayWasButton(
        text = stringResource(R.string.onboarding_welcome_cta),
        onClick = onWelcomeContinue,
    )
}

@Composable
private fun FocusPickStepContent(
    state: OnboardingDialogState,
    onFocusOptionSelected: (Focus) -> Unit,
    onConfirmSelection: () -> Unit,
    onRetrySave: () -> Unit,
) {
    TodayWasText(text = stringResource(R.string.onboarding_focus_pick_title))
    for (focus in Focus.entries) {
        TodayWasRadioOption(
            text = focus.label(),
            selected = state.selectedFocusInDialog == focus,
            onClick = { onFocusOptionSelected(focus) },
        )
    }
    if (state.isSaving) {
        TodayWasLoadingIndicator()
    } else if (state.saveError) {
        TodayWasText(text = stringResource(R.string.onboarding_focus_pick_error))
        TodayWasButton(
            text = stringResource(R.string.onboarding_focus_pick_retry),
            onClick = onRetrySave,
        )
    } else {
        TodayWasButton(
            text = stringResource(R.string.onboarding_focus_pick_confirm),
            onClick = onConfirmSelection,
            enabled = state.selectedFocusInDialog != null,
        )
    }
}

@Composable
private fun AccountInfoStepContent(
    onCreateAccountClicked: () -> Unit,
    onAccountContinue: () -> Unit,
) {
    TodayWasText(text = stringResource(R.string.onboarding_account_description))
    TodayWasTextButton(
        text = stringResource(R.string.onboarding_account_create_cta),
        onClick = onCreateAccountClicked,
    )
    TodayWasButton(
        text = stringResource(R.string.onboarding_account_continue_cta),
        onClick = onAccountContinue,
    )
}

@Composable
private fun AllSetStepContent(onFinishOnboarding: () -> Unit) {
    TodayWasText(text = stringResource(R.string.onboarding_all_set_title))
    TodayWasButton(
        text = stringResource(R.string.onboarding_all_set_cta),
        onClick = onFinishOnboarding,
    )
}

@Composable
private fun Focus.label(): String =
    when (this) {
        Focus.JOURNAL -> stringResource(R.string.focus_journal)
        Focus.HABIT -> stringResource(R.string.focus_habit)
        Focus.BOTH -> stringResource(R.string.focus_both)
    }

private class OnboardingDialogPreviewStateProvider : PreviewParameterProvider<OnboardingDialogState> {
    override val values =
        sequenceOf(
            previewState(step = OnboardingStep.WELCOME),
            previewState(step = OnboardingStep.FOCUS_PICK),
            previewState(step = OnboardingStep.FOCUS_PICK, selectedFocusInDialog = Focus.JOURNAL),
            previewState(step = OnboardingStep.FOCUS_PICK, selectedFocusInDialog = Focus.HABIT, isSaving = true),
            previewState(step = OnboardingStep.FOCUS_PICK, selectedFocusInDialog = Focus.BOTH, saveError = true),
            previewState(step = OnboardingStep.ACCOUNT_INFO),
            previewState(step = OnboardingStep.ALL_SET),
            previewState(mode = OnboardingMode.REPICK, step = OnboardingStep.FOCUS_PICK, selectedFocusInDialog = Focus.HABIT),
        )
}

private fun previewState(
    mode: OnboardingMode = OnboardingMode.MANDATORY,
    step: OnboardingStep,
    selectedFocusInDialog: Focus? = null,
    isSaving: Boolean = false,
    saveError: Boolean = false,
) = OnboardingDialogState(
    onboardingMode = mode,
    onboardingStep = step,
    selectedFocusInDialog = selectedFocusInDialog,
    isSaving = isSaving,
    saveError = saveError,
)

@PreviewLightDark
@Composable
private fun OnboardingDialogPreview(
    @PreviewParameter(OnboardingDialogPreviewStateProvider::class) state: OnboardingDialogState,
) {
    TodayWasTheme {
        OnboardingDialog(
            state = state,
            onDismiss = {},
            onStepBack = {},
            onSkip = {},
            onWelcomeContinue = {},
            onFocusOptionSelected = {},
            onConfirmSelection = {},
            onRetrySave = {},
            onCreateAccountClicked = {},
            onAccountContinue = {},
            onFinishOnboarding = {},
        )
    }
}
