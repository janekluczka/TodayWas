package pl.luczka.todaywas.core.designsystem.components.buttons

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
import pl.luczka.todaywas.core.designsystem.components.progress.DsLoadingIndicator
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsButtonWithLoading(
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
            DsLoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(20.dp),
            )
        } else {
            DsText(text = text)
        }
    }
}

private class DsButtonWithLoadingPreviewStateProvider : PreviewParameterProvider<Pair<Boolean, Boolean>> {
    override val values = sequenceOf(
        true to false,
        false to false,
        true to true,
    )
}

@PreviewLightDark
@Composable
private fun DsButtonWithLoadingPreview(
    @PreviewParameter(DsButtonWithLoadingPreviewStateProvider::class) state: Pair<Boolean, Boolean>,
) {
    val (enabled, loading) = state
    DesignSystemPreviewTheme {
        DsButtonWithLoading(
            text = "Confirm",
            onClick = {},
            enabled = enabled,
            loading = loading,
        )
    }
}
