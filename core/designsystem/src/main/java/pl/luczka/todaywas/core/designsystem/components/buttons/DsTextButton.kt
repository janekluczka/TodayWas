package pl.luczka.todaywas.core.designsystem.components.buttons

import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        DsText(text = text)
    }
}

@PreviewLightDark
@Composable
private fun DsTextButtonPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) enabled: Boolean,
) {
    DsTheme {
        DsTextButton(
            text = "Skip",
            onClick = {},
            enabled = enabled,
        )
    }
}
