package pl.luczka.todaywas.data.mapper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.util.SyncMeta
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
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) },
)

fun List<HabitEntity>.toDomain(): List<Habit> = map { it.toDomain() }

fun Flow<List<HabitEntity>>.toDomain(): Flow<List<Habit>> = map { it.toDomain() }

fun HabitEntity.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isDeleted = deletedAt != null,
)

fun List<HabitEntity>.toSyncMeta(): List<SyncMeta> = map { it.toSyncMeta() }
