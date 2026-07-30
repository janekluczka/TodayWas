package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasButtonWithLoading(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.animateContentSize(),
    ) {
        if (loading) {
            TodayWasLoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(20.dp),
            )
        } else {
            TodayWasText(text = text)
        }
    }
}

private class TodayWasButtonWithLoadingPreviewStateProvider : PreviewParameterProvider<Pair<Boolean, Boolean>> {
    override val values = sequenceOf(
        true to false,
        false to false,
        true to true,
    )
}

@PreviewLightDark
@Composable
private fun TodayWasButtonWithLoadingPreview(
    @PreviewParameter(TodayWasButtonWithLoadingPreviewStateProvider::class) state: Pair<Boolean, Boolean>,
) {
    val (enabled, loading) = state
    DesignSystemPreviewTheme {
        TodayWasButtonWithLoading(
            text = "Confirm",
            onClick = {},
            enabled = enabled,
            loading = loading,
        )
    }
}
