package pl.luczka.todaywas.data.repository

import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.domain.model.Habit
import java.time.Instant

fun Habit.toRemoteDto(userId: String): HabitRemoteDto = HabitRemoteDto(
    id = id,
    userId = userId,
    name = name,
    description = description,
    type = type.name,
    scaleMin = scaleMin,
    scaleMax = scaleMax,
    createdAt = createdAt.toString(),
)

fun HabitRemoteDto.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    description = description,
    type = type,
    scaleMin = scaleMin,
    scaleMax = scaleMax,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
)
