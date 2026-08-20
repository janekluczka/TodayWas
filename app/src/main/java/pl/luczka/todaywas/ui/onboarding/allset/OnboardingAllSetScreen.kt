package pl.luczka.todaywas.ui.onboarding.allset

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
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.onboarding.AllSetReason

@Composable
fun OnboardingAllSetScreen(
    reason: AllSetReason,
    onFinished: () -> Unit,
    viewModel: OnboardingAllSetViewModel =
        hiltViewModel<OnboardingAllSetViewModel, OnboardingAllSetViewModel.Factory> { factory ->
            factory.create(reason)
        },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingAllSetUiEvent.Finished -> onFinished()
            }
        }
    }

    OnboardingAllSetScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun OnboardingAllSetScreenContent(
    uiState: OnboardingAllSetUiState,
    onIntent: (OnboardingAllSetIntent) -> Unit,
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
                text = stringResource(R.string.onboarding_all_set_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            DsText(
                text = allSetMessage(uiState.reason, uiState.email),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = DsSpacing.space400),
            )
            DsButton(
                text = stringResource(R.string.onboarding_all_set_cta),
                onClick = { onIntent(OnboardingAllSetIntent.GetStartedClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.space600),
            )
        }
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
    AllSetReason.ACCOUNT_CREATED -> stringResource(
        R.string.onboarding_all_set_account_created_message,
    )
}

private class OnboardingAllSetUiStatePreviewProvider :
    PreviewParameterProvider<OnboardingAllSetUiState> {
    override val values = sequenceOf(
        OnboardingAllSetUiState(reason = AllSetReason.NO_ACCOUNT),
        OnboardingAllSetUiState(reason = AllSetReason.SIGNED_IN, email = "person@example.com"),
        OnboardingAllSetUiState(reason = AllSetReason.ACCOUNT_CREATED),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingAllSetScreenPreview(
    @PreviewParameter(OnboardingAllSetUiStatePreviewProvider::class) state: OnboardingAllSetUiState,
) {
    DsTheme {
        OnboardingAllSetScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
