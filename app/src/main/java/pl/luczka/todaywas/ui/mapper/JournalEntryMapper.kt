package pl.luczka.todaywas.ui.mapper

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.ui.model.JournalEntryUiState

fun JournalEntry.toUiState(): JournalEntryUiState = JournalEntryUiState(
    id = id,
    date = date,
    formattedDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
    text = text,
    createdAt = createdAt,
)
