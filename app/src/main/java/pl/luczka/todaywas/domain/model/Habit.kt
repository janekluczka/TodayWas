package pl.luczka.todaywas.domain.model

import java.time.Instant

// updatedAt/deletedAt are data-layer sync plumbing only (conflict-resolution metadata for
// syncWithRemote()) - never surfaced through any ui/mapper/ or UiState.
data class Habit(
    val id: String,
    val name: String,
    val description: String?,
    val type: HabitType,
    val scaleMin: Int?,
    val scaleMax: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)
