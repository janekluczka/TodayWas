package pl.luczka.todaywas.core.designsystem.components.navigation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsNavigationRail(
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationRail(
        header = header,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun ColumnScope.DsNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
) {
    NavigationRailItem(
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
private fun DsNavigationRailPreview() {
    DsTheme {
        DsNavigationRail {
            DsNavigationRailItem(
                selected = true,
                onClick = {},
                icon = { DsIcon(imageVector = Icons.Default.Edit, contentDescription = null) },
                label = { DsText(text = "Journal") },
            )
            DsNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { DsIcon(imageVector = Icons.Default.Add, contentDescription = null) },
                label = { DsText(text = "Habits") },
            )
        }
    }
}
