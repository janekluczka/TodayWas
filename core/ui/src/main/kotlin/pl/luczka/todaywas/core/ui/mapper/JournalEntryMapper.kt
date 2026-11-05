package pl.luczka.todaywas.core.ui.mapper

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import pl.luczka.todaywas.core.domain.model.JournalEntry
import pl.luczka.todaywas.core.ui.model.JournalEntryUiState

fun JournalEntry.toUiState(): JournalEntryUiState = JournalEntryUiState(
    id = id,
    date = date,
    formattedDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
    text = text,
    createdAt = createdAt,
)
