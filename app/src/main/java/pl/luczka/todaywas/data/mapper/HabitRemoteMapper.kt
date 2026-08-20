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

fun HabitEntity.toRemoteDto(userId: String): HabitRemoteDto = toDomain().toRemoteDto(userId)

fun List<HabitEntity>.toRemoteDto(userId: String): List<HabitRemoteDto> =
    map { it.toRemoteDto(userId) }

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

fun List<HabitRemoteDto>.toEntity(): List<HabitEntity> = map { it.toEntity() }

fun HabitRemoteDto.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.parse(updatedAt),
    isDeleted = deletedAt != null,
)

fun List<HabitRemoteDto>.toSyncMeta(): List<SyncMeta> = map { it.toSyncMeta() }
