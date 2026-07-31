package pl.luczka.todaywas.domain.model

import java.time.Instant

data class Habit(
    val id: Long,
    val name: String,
    val description: String?,
    val type: HabitType,
    val scaleMin: Int?,
    val scaleMax: Int?,
    val createdAt: Instant,
)
