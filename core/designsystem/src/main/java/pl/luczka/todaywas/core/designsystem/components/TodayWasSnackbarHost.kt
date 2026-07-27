package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier)
}

@PreviewLightDark
@Composable
private fun TodayWasSnackbarHostPreview() {
    DesignSystemPreviewTheme {
        val hostState = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { hostState.showSnackbar("Something went wrong. Please try again.") }
        TodayWasSnackbarHost(hostState = hostState)
    }
}
