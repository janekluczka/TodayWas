package pl.luczka.todaywas.core.designsystem.components.progress

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    if (progress != null) {
        LinearProgressIndicator(
            progress = progress,
            color = color,
            trackColor = trackColor,
            modifier = modifier,
        )
    } else {
        LinearProgressIndicator(
            color = color,
            trackColor = trackColor,
            modifier = modifier,
        )
    }
}

@PreviewLightDark
@Composable
private fun DsLinearProgressIndicatorPreview() {
    DesignSystemPreviewTheme {
        DsLinearProgressIndicator(progress = { 0.6f })
    }
}
