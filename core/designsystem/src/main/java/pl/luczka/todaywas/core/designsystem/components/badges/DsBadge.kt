package pl.luczka.todaywas.core.designsystem.components.badges

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

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
    DsTheme {
        DsBadge(badgeContent = { DsText(text = "3") }) {
            DsIcon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
            )
        }
    }
}
