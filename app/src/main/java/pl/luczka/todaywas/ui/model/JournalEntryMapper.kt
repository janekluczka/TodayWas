package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun JournalEntry.toUiState(): JournalEntryUiState = JournalEntryUiState(
    id = id,
    date = date,
    formattedDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
    text = text,
    createdAt = createdAt,
)
