package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsModalNavigationDrawer(
    drawerContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerContent = { ModalDrawerSheet(content = drawerContent) },
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        modifier = modifier,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun DsModalNavigationDrawerPreview() {
    DesignSystemPreviewTheme {
        DsModalNavigationDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            drawerContent = {
                DsText(
                    text = "Drawer content",
                    modifier = Modifier.padding(16.dp),
                )
            },
        ) {
            DsText(text = "Screen content")
        }
    }
}
