package pl.luczka.todaywas.ui.onboarding.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButton
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

// No ViewModel: purely static content with a single navigation action, nothing to observe or
// persist — the system back gesture already exits the app for free (this is the root of the
// onboarding back stack, so there's nothing for Nav3 to pop).
@Composable
fun OnboardingWelcomeScreen(onGetStartedClicked: () -> Unit) {
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
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            DsText(
                text = stringResource(R.string.onboarding_welcome_description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = DsSpacing.space400),
            )
            DsButton(
                text = stringResource(R.string.onboarding_welcome_cta),
                onClick = onGetStartedClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.space600),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OnboardingWelcomeScreenPreview() {
    DsTheme {
        OnboardingWelcomeScreen(onGetStartedClicked = {})
    }
}
