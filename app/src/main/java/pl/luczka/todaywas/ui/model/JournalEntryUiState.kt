package pl.luczka.todaywas.ui.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate

@Immutable
data class JournalEntryUiState(
    val id: String,
    val date: LocalDate,
    val formattedDate: String,
    val text: String,
    val createdAt: Instant,
)
