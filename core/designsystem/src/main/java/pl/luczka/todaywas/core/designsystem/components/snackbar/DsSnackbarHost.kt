package pl.luczka.todaywas.core.designsystem.components.snackbar

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsSnackbarHostPreview() {
    DsTheme {
        val hostState = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { hostState.showSnackbar("Something went wrong. Please try again.") }
        DsSnackbarHost(hostState = hostState)
    }
}
