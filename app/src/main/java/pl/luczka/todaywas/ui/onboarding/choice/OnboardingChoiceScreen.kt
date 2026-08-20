package pl.luczka.todaywas.ui.onboarding.choice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.onboarding.AllSetReason

@Composable
fun OnboardingChoiceScreen(
    onNavigateToAccountSetup: () -> Unit,
    onFinished: (AllSetReason) -> Unit,
    viewModel: OnboardingChoiceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingChoiceUiEvent.NavigateToAccountSetup -> onNavigateToAccountSetup()
                is OnboardingChoiceUiEvent.Finished -> onFinished(event.reason)
            }
        }
    }

    OnboardingChoiceScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun OnboardingChoiceScreenContent(
    uiState: OnboardingChoiceUiState,
    onIntent: (OnboardingChoiceIntent) -> Unit,
) {
    DsScaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(DsSpacing.space600),
        ) {
            DsText(
                text = stringResource(R.string.onboarding_account_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            DsText(
                text = stringResource(R.string.onboarding_account_description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = DsSpacing.space400),
            )
            if (uiState.saveError) {
                DsText(
                    text = stringResource(R.string.onboarding_account_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = DsSpacing.space400),
                )
            }
            DsButton(
                text = stringResource(R.string.onboarding_account_continue_without_account_cta),
                onClick = { onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked) },
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.space600),
            )
            DsTextButton(
                text = stringResource(R.string.onboarding_account_signin_signup_cta),
                onClick = { onIntent(OnboardingChoiceIntent.SignInSignUpClicked) },
                enabled = !uiState.isSaving,
                modifier = Modifier.padding(top = DsSpacing.space200),
            )
        }
    }
}

private class OnboardingChoiceUiStatePreviewProvider :
    PreviewParameterProvider<OnboardingChoiceUiState> {
    override val values = sequenceOf(
        OnboardingChoiceUiState(),
        OnboardingChoiceUiState(isSaving = true),
        OnboardingChoiceUiState(saveError = true),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingChoiceScreenPreview(
    @PreviewParameter(OnboardingChoiceUiStatePreviewProvider::class) state: OnboardingChoiceUiState,
) {
    DsTheme {
        OnboardingChoiceScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
