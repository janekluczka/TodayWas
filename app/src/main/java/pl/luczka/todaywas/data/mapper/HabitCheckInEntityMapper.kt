package pl.luczka.todaywas.data.mapper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.util.SyncMeta
import pl.luczka.todaywas.domain.model.HabitCheckIn
import java.time.Instant
import java.time.LocalDate

fun HabitCheckInEntity.toDomain(): HabitCheckIn = HabitCheckIn(
    id = id,
    habitId = habitId,
    date = LocalDate.parse(date),
    value = value,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) },
)

fun List<HabitCheckInEntity>.toDomain(): List<HabitCheckIn> = map { it.toDomain() }

fun Flow<List<HabitCheckInEntity>>.toDomain(): Flow<List<HabitCheckIn>> = map { it.toDomain() }

fun HabitCheckInEntity.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isDeleted = deletedAt != null,
)

fun List<HabitCheckInEntity>.toSyncMeta(): List<SyncMeta> = map { it.toSyncMeta() }
