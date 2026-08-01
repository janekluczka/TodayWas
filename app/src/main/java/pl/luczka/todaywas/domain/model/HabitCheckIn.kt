package pl.luczka.todaywas.domain.model

import java.time.Instant
import java.time.LocalDate

data class HabitCheckIn(
    val id: Long,
    val habitId: Long,
    val date: LocalDate,
    val value: Int,
    val createdAt: Instant,
)
