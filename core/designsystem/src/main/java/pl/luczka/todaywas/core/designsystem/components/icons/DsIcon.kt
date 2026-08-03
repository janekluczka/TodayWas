package pl.luczka.todaywas.core.designsystem.components.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsIconPreview() {
    DsTheme {
        DsIcon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Change focus",
        )
    }
}
