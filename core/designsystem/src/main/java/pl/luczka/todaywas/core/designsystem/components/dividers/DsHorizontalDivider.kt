package pl.luczka.todaywas.core.designsystem.components.dividers

import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) {
    HorizontalDivider(
        thickness = thickness,
        color = color,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsHorizontalDividerPreview() {
    DesignSystemPreviewTheme {
        DsHorizontalDivider()
    }
}
