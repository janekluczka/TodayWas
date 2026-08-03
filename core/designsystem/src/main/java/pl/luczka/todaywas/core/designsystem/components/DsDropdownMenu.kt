package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun DsDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { DsText(text = text) },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsDropdownMenuPreview() {
    DesignSystemPreviewTheme {
        var expanded by remember { mutableStateOf(true) }
        DsIconButton(onClick = { expanded = true }) {
            DsText(text = "⋮")
        }
        DsDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DsDropdownMenuItem(text = "Edit", onClick = {})
            DsDropdownMenuItem(text = "Delete", onClick = {})
        }
    }
}
