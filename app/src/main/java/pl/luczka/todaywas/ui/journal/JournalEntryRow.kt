package pl.luczka.todaywas.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.JournalEntryUiState

// Shared by Main's capped Journal section and the full Journal list screen.
@Composable
fun JournalEntryRow(
    entry: JournalEntryUiState,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(DsSpacing.space400),
    ) {
        DsText(text = entry.formattedDate)
        DsText(
            text = entry.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
