package pl.luczka.todaywas.data.repository

import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.domain.model.HabitCheckIn
import java.time.Instant
import java.time.LocalDate

fun HabitCheckInEntity.toDomain(): HabitCheckIn = HabitCheckIn(
    id = id,
    habitId = habitId,
    date = LocalDate.parse(date),
    value = value,
    createdAt = Instant.ofEpochMilli(createdAt),
)
