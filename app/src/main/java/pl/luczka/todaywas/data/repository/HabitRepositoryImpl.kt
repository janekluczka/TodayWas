package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
import pl.luczka.todaywas.data.util.TOMBSTONE_GC_WINDOW
import pl.luczka.todaywas.data.util.mergeForSync
import pl.luczka.todaywas.data.util.remoteCall
import pl.luczka.todaywas.di.ApplicationScope
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
    @ApplicationScope private val syncScope: CoroutineScope,
) : HabitRepository {

    // Guards against a bulk syncWithRemote() push-then-pull racing an individual
    // pushInBackground() for the same row: without this, a syncWithRemote() call that snapshot
    // a row before a concurrent edit can push/pull that stale snapshot back over the edit.
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
        if (result.isSuccess) pushHabitInBackground(entity)
        return result
    }

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> =
        local.observeCheckIns().map { entities -> entities.map { it.toDomain() } }

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
        if (result.isSuccess) pushCheckInsInBackground(entities)
        return result
    }

    override suspend fun updateCheckIn(
        habitId: String,
        date: LocalDate,
        value: Int,
    ): Result<Unit> {
        val result = local.updateCheckIn(habitId, date, value, Instant.now().toEpochMilli())
        result.onSuccess { pushCheckInsInBackground(listOf(it)) }
        return result.map { }
    }

    override suspend fun deleteHabit(id: String): Result<Unit> {
        val deletedAt = Instant.now().toEpochMilli()
        val result = local.deleteHabitAndCheckIns(id, deletedAt)
        result.onSuccess { (habit, checkIns) ->
            // Both the habit and its check-ins need pushing - the live ON DELETE CASCADE FK only
            // fires on a real remote DELETE, not the soft-delete UPDATE this now performs, so the
            // cascade has to happen explicitly from the app on both local and remote sides.
            habit?.let { pushHabitInBackground(it) }
            if (checkIns.isNotEmpty()) pushCheckInsInBackground(checkIns)
        }
        return result.map { }
    }

    override suspend fun deleteCheckIn(
        habitId: String,
        date: LocalDate,
    ): Result<Unit> {
        val result = local.deleteCheckIn(habitId, date, Instant.now().toEpochMilli())
        // No dedicated remote delete call - the row's own deletedAt is the tombstone, carried to
        // remote by the same pushCheckInsInBackground upsert path used for adds/edits.
        result.onSuccess { pushCheckInsInBackground(listOf(it)) }
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

                // Known limitation: the remote habit_check_ins.habit_id FK still has a live
                // ON DELETE CASCADE, so purging a habit here can hard-delete check-in rows that
                // never went through their own tombstone/GC lifecycle (e.g. one written by another
                // device that hadn't yet synced the habit's deletion). Currently unreachable through
                // the app's own UI (a soft-deleted habit is never offered for new check-ins), but
                // the FK itself isn't - a real fix means dropping the CASCADE and redesigning purge
                // ordering, deferred to architecture-hardening alongside the related syncMutex/
                // durability work already scoped there.
                val cutoff = Instant.now().minus(TOMBSTONE_GC_WINDOW)
                local.purgeDeletedBefore(cutoff.toEpochMilli())
                remoteHabitDataSource.purgeDeletedBefore(userId, cutoff.toString()).getOrThrow()
                remoteHabitCheckInDataSource.purgeDeletedBefore(userId, cutoff.toString()).getOrThrow()
            }
        }
    }

    override suspend fun clearLocal(): Result<Unit> = local.clearAll()

    // Best-effort - failures are silently swallowed since the local write already succeeded;
    // the next successful write (or the next sync pass) naturally retries via upsert. Skipped
    // entirely (rather than awaiting the lock) while a syncWithRemote() is in flight, since that
    // sync's own push/pull already covers this row - the edit isn't lost, just picked up on the
    // next successful push or sync pass instead of duplicating work against a stale snapshot.
    private fun pushHabitInBackground(entity: HabitEntity) {
        val userId = authRepository.currentUserId() ?: return
        syncScope.launch {
            if (syncMutex.tryLock()) {
                try {
                    remoteHabitDataSource.upsert(listOf(entity.toDomain().toRemoteDto(userId)))
                } finally {
                    syncMutex.unlock()
                }
            }
        }
    }

    private fun pushCheckInsInBackground(entities: List<HabitCheckInEntity>) {
        val userId = authRepository.currentUserId() ?: return
        syncScope.launch {
            if (syncMutex.tryLock()) {
                try {
                    remoteHabitCheckInDataSource.upsert(entities.map { it.toDomain().toRemoteDto(userId) })
                } finally {
                    syncMutex.unlock()
                }
            }
        }
    }
}
