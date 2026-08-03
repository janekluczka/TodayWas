package pl.luczka.todaywas.core.designsystem.components.chips

import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { DsText(text = text) },
        enabled = enabled,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsFilterChipPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) selected: Boolean,
) {
    DesignSystemPreviewTheme {
        DsFilterChip(
            text = "Journaling",
            selected = selected,
            onClick = {},
        )
    }
}
