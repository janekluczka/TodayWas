package pl.luczka.todaywas.core.designsystem.components.fab

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsExtendedFloatingActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        DsText(text = text)
    }
}

@PreviewLightDark
@Composable
private fun DsExtendedFloatingActionButtonPreview() {
    DesignSystemPreviewTheme {
        DsExtendedFloatingActionButton(
            text = "Add journal",
            onClick = {},
        )
    }
}
