package pl.luczka.todaywas.core.designsystem.components.navigation

import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    SecondaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        tabs = tabs,
    )
}

@Composable
fun DsTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        text = text,
        icon = icon,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsTabRowPreview() {
    DesignSystemPreviewTheme {
        DsTabRow(selectedTabIndex = 0) {
            DsTab(
                selected = true,
                onClick = {},
                text = { DsText(text = "Journal") },
            )
            DsTab(
                selected = false,
                onClick = {},
                text = { DsText(text = "Habits") },
            )
        }
    }
}
