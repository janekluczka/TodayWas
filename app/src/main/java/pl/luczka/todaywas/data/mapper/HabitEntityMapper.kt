package pl.luczka.todaywas.data.mapper

import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitType
import java.time.Instant

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    description = description,
    type = HabitType.valueOf(type),
    scaleMin = scaleMin,
    scaleMax = scaleMax,
    createdAt = Instant.ofEpochMilli(createdAt),
)
