package pl.luczka.todaywas.core.designsystem.components.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

// A single icon+label(+trailing) row meant for DsSectionedList — settings-style entries (account
// identity, sign out, future preference items), not the richer, feature-specific rows like
// JournalEntryRow/HabitRow.
@Composable
fun DsListItem(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(DsSpacing.space400),
    ) {
        if (leadingIcon != null) {
            DsIcon(imageVector = leadingIcon, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(DsSpacing.space400))
        }
        DsText(text = text, color = contentColor, modifier = Modifier.weight(1f))
        trailingContent?.invoke()
    }
}

@PreviewLightDark
@Composable
private fun DsListItemPreview() {
    DsTheme {
        DsListItem(
            text = "person@example.com",
            leadingIcon = Icons.Default.Person,
        )
    }
}
