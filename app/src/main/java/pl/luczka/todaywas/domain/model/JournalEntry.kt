package pl.luczka.todaywas.domain.model

import java.time.Instant
import java.time.LocalDate

// updatedAt/deletedAt are data-layer sync plumbing only (conflict-resolution metadata for
// syncWithRemote()) - never surfaced through any ui/mapper/ or UiState.
data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val text: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)
