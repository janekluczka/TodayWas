package pl.luczka.todaywas.data.mapper

import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.remote.dto.HabitRemoteDto
import pl.luczka.todaywas.data.util.SyncMeta
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
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString(),
)

fun HabitRemoteDto.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    description = description,
    type = type,
    scaleMin = scaleMin,
    scaleMax = scaleMax,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() },
)

fun HabitRemoteDto.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.parse(updatedAt),
    isDeleted = deletedAt != null,
)
