package pl.luczka.todaywas.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.ui.theme.TodayWasTheme

@Composable
fun MainScreen() {
    TodayWasScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TodayWasTopBar(title = stringResource(R.string.main_top_bar_title)) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            TodayWasText(text = stringResource(R.string.main_empty_state))
        }
    }
}

@PreviewLightDark
@Composable
private fun MainScreenPreview() {
    TodayWasTheme {
        MainScreen()
    }
}
