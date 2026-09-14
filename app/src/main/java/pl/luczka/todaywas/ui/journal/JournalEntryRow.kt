package pl.luczka.todaywas.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.JournalEntryUiState

// Shared by Main's capped Journal section and the full Journal list screen. Shows only the date —
// the entry's own text is reserved for opening it, not previewed in a list.
@Composable
fun JournalEntryRow(
    entry: JournalEntryUiState,
    onClick: () -> Unit,
) {
    DsText(
        text = entry.formattedDate,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(DsSpacing.space400),
    )
}
