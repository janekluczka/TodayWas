package pl.luczka.todaywas.core.designsystem.components.progress

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
) {
    CircularProgressIndicator(
        color = color,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsLoadingIndicatorPreview() {
    DesignSystemPreviewTheme {
        DsLoadingIndicator()
    }
}
