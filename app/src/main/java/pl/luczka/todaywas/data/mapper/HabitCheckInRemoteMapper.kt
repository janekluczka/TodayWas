package pl.luczka.todaywas.data.mapper

import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.remote.dto.HabitCheckInRemoteDto
import pl.luczka.todaywas.domain.model.HabitCheckIn
import java.time.Instant

fun HabitCheckIn.toRemoteDto(userId: String): HabitCheckInRemoteDto = HabitCheckInRemoteDto(
    id = id,
    userId = userId,
    habitId = habitId,
    date = date.toString(),
    value = value,
    createdAt = createdAt.toString(),
)

fun HabitCheckInRemoteDto.toEntity(): HabitCheckInEntity = HabitCheckInEntity(
    id = id,
    habitId = habitId,
    date = date,
    value = value,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
)
