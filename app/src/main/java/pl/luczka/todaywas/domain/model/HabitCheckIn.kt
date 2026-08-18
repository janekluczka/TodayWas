package pl.luczka.todaywas.domain.model

import java.time.Instant
import java.time.LocalDate

// updatedAt/deletedAt are data-layer sync plumbing only (conflict-resolution metadata for
// syncWithRemote()) - never surfaced through any ui/mapper/ or UiState.
data class HabitCheckIn(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val value: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)
