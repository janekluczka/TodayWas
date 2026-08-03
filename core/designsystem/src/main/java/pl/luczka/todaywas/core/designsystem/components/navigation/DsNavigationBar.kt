package pl.luczka.todaywas.core.designsystem.components.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        content = content,
    )
}

@Composable
fun RowScope.DsNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsNavigationBarPreview() {
    DesignSystemPreviewTheme {
        DsNavigationBar {
            DsNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { DsIcon(imageVector = Icons.Default.Edit, contentDescription = null) },
                label = { DsText(text = "Journal") },
            )
            DsNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { DsIcon(imageVector = Icons.Default.Add, contentDescription = null) },
                label = { DsText(text = "Habits") },
            )
        }
    }
}
