package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.luczka.todaywas.data.local.api.LocalHabitDataSource
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.mapper.toDomain
import pl.luczka.todaywas.data.mapper.toEntity
import pl.luczka.todaywas.data.mapper.toRemoteDto
import pl.luczka.todaywas.data.mapper.toSyncMeta
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSource
import pl.luczka.todaywas.data.util.SyncScheduler
import pl.luczka.todaywas.data.util.TOMBSTONE_GC_WINDOW
import pl.luczka.todaywas.data.util.mergeForSync
import pl.luczka.todaywas.data.util.remoteCall
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.HabitRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class HabitRepositoryImpl @Inject constructor(
    private val local: LocalHabitDataSource,
    private val remoteHabitDataSource: RemoteHabitDataSource,
    private val remoteHabitCheckInDataSource: RemoteHabitCheckInDataSource,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
) : HabitRepository {

    // Guards against two concurrent syncWithRemote() calls racing each other - writes no longer
    // push individually in the background, so there's nothing else left for this to race against.
    private val syncMutex = Mutex()

    override fun observeHabits(): Flow<List<Habit>> = local.observeHabits().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun createHabit(
        name: String,
        description: String?,
        type: HabitType,
        scaleMin: Int?,
        scaleMax: Int?,
    ): Result<Unit> {
        val now = Instant.now().toEpochMilli()
        val entity = HabitEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            type = type.name,
            scaleMin = scaleMin,
            scaleMax = scaleMax,
            createdAt = now,
            updatedAt = now,
        )
        val result = local.insertHabit(entity)
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result
    }

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> =
        local.observeCheckIns().map { entities -> entities.map { it.toDomain() } }

    override fun observeCheckIns(habitId: String): Flow<List<HabitCheckIn>> = local.observeCheckIns().map { entities ->
        entities.filter { it.habitId == habitId }.map { it.toDomain() }
    }

    override suspend fun addCheckIns(
        date: LocalDate,
        values: Map<String, Int>,
    ): Result<Unit> {
        val createdAt = Instant.now().toEpochMilli()
        val entities = values.map { (habitId, value) ->
            HabitCheckInEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                date = date.toString(),
                value = value,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }
        val result = local.insertCheckIns(entities)
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result
    }

    override suspend fun updateCheckIn(
        habitId: String,
        date: LocalDate,
        value: Int,
    ): Result<Unit> {
        val result = local.updateCheckIn(habitId, date, value, Instant.now().toEpochMilli())
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result.map { }
    }

    override suspend fun deleteHabit(id: String): Result<Unit> {
        val deletedAt = Instant.now().toEpochMilli()
        val result = local.deleteHabitAndCheckIns(id, deletedAt)
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result.map { }
    }

    override suspend fun deleteCheckIn(
        habitId: String,
        date: LocalDate,
    ): Result<Unit> {
        val result = local.deleteCheckIn(habitId, date, Instant.now().toEpochMilli())
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result.map { }
    }

    override suspend fun syncWithRemote(): Result<Unit> {
        val userId = authRepository.currentUserId() ?: return Result.success(Unit)
        return syncMutex.withLock {
            remoteCall {
                val localHabits = local.getAllHabitsIncludingDeleted()
                val remoteHabits = remoteHabitDataSource.fetchAll(userId).getOrThrow()
                val habitDecision = mergeForSync(localHabits.map { it.toSyncMeta() }, remoteHabits.map { it.toSyncMeta() })
                val habitsToPush = localHabits.filter { it.id in habitDecision.pushIds }
                if (habitsToPush.isNotEmpty()) {
                    remoteHabitDataSource.upsert(habitsToPush.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()
                }
                val habitsToApply = remoteHabits.filter { it.id in habitDecision.applyIds }

                val localCheckIns = local.getAllCheckInsIncludingDeleted()
                val remoteCheckIns = remoteHabitCheckInDataSource.fetchAll(userId).getOrThrow()
                val checkInDecision = mergeForSync(localCheckIns.map { it.toSyncMeta() }, remoteCheckIns.map { it.toSyncMeta() })
                val checkInsToPush = localCheckIns.filter { it.id in checkInDecision.pushIds }
                if (checkInsToPush.isNotEmpty()) {
                    remoteHabitCheckInDataSource.upsert(checkInsToPush.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()
                }
                val checkInsToApply = remoteCheckIns.filter { it.id in checkInDecision.applyIds }

                local.applyRemoteSnapshot(habitsToApply.map { it.toEntity() }, checkInsToApply.map { it.toEntity() })

                val cutoff = Instant.now().minus(TOMBSTONE_GC_WINDOW)
                local.purgeDeletedBefore(cutoff.toEpochMilli())
                // Check-ins must purge before habits: habit_check_ins.habit_id is now
                // ON DELETE NO ACTION (see Phase 2), so deleting a habit row while any check-in
                // still references it - even one with its own, more-recent tombstone - throws a
                // foreign key violation rather than silently cascading.
                remoteHabitCheckInDataSource.purgeDeletedBefore(userId, cutoff.toString()).getOrThrow()
                remoteHabitDataSource.purgeDeletedBefore(userId, cutoff.toString()).getOrThrow()
            }
        }
    }

    override suspend fun clearLocal(): Result<Unit> = local.clearAll()

    private fun scheduleSyncIfSignedIn() {
        if (authRepository.currentUserId() != null) syncScheduler.scheduleSync()
    }
}
