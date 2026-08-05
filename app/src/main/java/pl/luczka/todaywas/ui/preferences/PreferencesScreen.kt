package pl.luczka.todaywas.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
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
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.cards.DsCard
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.AuthStateUi

@Composable
fun PreferencesScreen(
    onAccountClicked: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PreferencesUiEvent.NavigateToAccount -> onAccountClicked()
            }
        }
    }

    PreferencesScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

// A settings screen (account, plus future app-settings sections) — the account card below
// redirects to a dedicated Account screen rather than hosting sign-in inline here.
@Composable
private fun PreferencesScreenContent(
    uiState: PreferencesUiState,
    onIntent: (PreferencesIntent) -> Unit,
) {
    DsScaffold(
        topBar = { DsTopBar(title = stringResource(R.string.preferences_top_bar_title)) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(DsSpacing.space600),
        ) {
            AccountCard(
                authState = uiState.authState,
                onClick = { onIntent(PreferencesIntent.AccountCardClicked) },
            )
        }
    }
}

@Composable
private fun AccountCard(
    authState: AuthStateUi,
    onClick: () -> Unit,
) {
    DsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(DsSpacing.space400),
        ) {
            DsIcon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.padding(end = DsSpacing.space400),
            )
            Column(modifier = Modifier.weight(1f)) {
                DsText(text = stringResource(R.string.preferences_account_section_title))
                DsText(text = accountSubtitle(authState))
            }
            DsIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun accountSubtitle(authState: AuthStateUi): String = when (authState) {
    AuthStateUi.Loading -> ""
    AuthStateUi.SignedOut -> stringResource(R.string.preferences_account_signed_out_subtitle)
    is AuthStateUi.SignedIn -> authState.email ?: stringResource(R.string.preferences_signed_in_no_email)
}

private class PreferencesUiStatePreviewProvider : PreviewParameterProvider<PreferencesUiState> {
    override val values = sequenceOf(
        PreferencesUiState(authState = AuthStateUi.Loading),
        PreferencesUiState(authState = AuthStateUi.SignedOut),
        PreferencesUiState(authState = AuthStateUi.SignedIn(email = "person@example.com")),
    )
}

@PreviewLightDark
@Composable
private fun PreferencesScreenPreview(
    @PreviewParameter(PreferencesUiStatePreviewProvider::class) state: PreferencesUiState,
) {
    DsTheme {
        PreferencesScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
