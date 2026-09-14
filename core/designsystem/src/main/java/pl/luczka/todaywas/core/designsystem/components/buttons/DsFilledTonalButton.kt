package pl.luczka.todaywas.core.designsystem.components.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

// A secondary-emphasis text button (e.g. "Regenerate" next to a primary "Accept") — lower
// visual weight than DsButton's filled container but still a solid tonal background, unlike
// DsTextButton's bare-label M3 TextButton.
@Composable
fun DsFilledTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        DsText(text = text)
    }
}

@PreviewLightDark
@Composable
private fun DsFilledTonalButtonPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) enabled: Boolean,
) {
    DsTheme {
        DsFilledTonalButton(
            text = "Regenerate",
            onClick = {},
            enabled = enabled,
        )
    }
}
