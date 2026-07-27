package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
) {
    CircularProgressIndicator(modifier = modifier, color = color)
}

@PreviewLightDark
@Composable
private fun TodayWasLoadingIndicatorPreview() {
    DesignSystemPreviewTheme {
        TodayWasLoadingIndicator()
    }
}
