package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsBadge(
    modifier: Modifier = Modifier,
    badgeContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BadgedBox(
        badge = { Badge(content = badgeContent) },
        modifier = modifier,
    ) {
        content()
    }
}

@PreviewLightDark
@Composable
private fun DsBadgePreview() {
    DesignSystemPreviewTheme {
        DsBadge(badgeContent = { DsText(text = "3") }) {
            DsIcon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
            )
        }
    }
}
