package pl.luczka.todaywas.data.habit.mapper

import java.time.Instant
import pl.luczka.todaywas.core.database.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.habit.remote.HabitCheckInRemoteDto
import pl.luczka.todaywas.domain.entry.util.SyncMeta
import pl.luczka.todaywas.domain.habit.model.HabitCheckIn

fun HabitCheckIn.toRemoteDto(userId: String): HabitCheckInRemoteDto = HabitCheckInRemoteDto(
    id = id,
    userId = userId,
    habitId = habitId,
    date = date.toString(),
    value = value,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString(),
)

fun HabitCheckInEntity.toRemoteDto(userId: String): HabitCheckInRemoteDto = toDomain().toRemoteDto(userId)

fun List<HabitCheckInEntity>.toRemoteDto(userId: String): List<HabitCheckInRemoteDto> = map { it.toRemoteDto(userId) }

fun HabitCheckInRemoteDto.toEntity(): HabitCheckInEntity = HabitCheckInEntity(
    id = id,
    habitId = habitId,
    date = date,
    value = value,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() },
)

fun List<HabitCheckInRemoteDto>.toEntity(): List<HabitCheckInEntity> = map { it.toEntity() }

fun HabitCheckInRemoteDto.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.parse(updatedAt),
    isDeleted = deletedAt != null,
)

fun List<HabitCheckInRemoteDto>.toSyncMeta(): List<SyncMeta> = map { it.toSyncMeta() }
