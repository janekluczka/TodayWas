package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.luczka.todaywas.data.local.dao.HabitCheckInDao
import pl.luczka.todaywas.data.local.dao.HabitDao
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.mapper.toDomain
import pl.luczka.todaywas.data.mapper.toEntity
import pl.luczka.todaywas.data.mapper.toRemoteDto
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSource
import pl.luczka.todaywas.data.util.remoteCall
import pl.luczka.todaywas.data.util.safeDbCall
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
    private val habitDao: HabitDao,
    private val habitCheckInDao: HabitCheckInDao,
    private val remoteHabitDataSource: RemoteHabitDataSource,
    private val remoteHabitCheckInDataSource: RemoteHabitCheckInDataSource,
    private val authRepository: AuthRepository,
    @ApplicationScope private val syncScope: CoroutineScope,
) : HabitRepository {

    // Guards against a bulk syncWithRemote() push-then-pull racing an individual
    // pushInBackground() for the same row: without this, a syncWithRemote() call that snapshot
    // a row before a concurrent edit can push/pull that stale snapshot back over the edit.
    private val syncMutex = Mutex()

    override fun observeHabits(): Flow<List<Habit>> = habitDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun createHabit(
        name: String,
        description: String?,
        type: HabitType,
        scaleMin: Int?,
        scaleMax: Int?,
    ): Result<Unit> {
        val entity = HabitEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            type = type.name,
            scaleMin = scaleMin,
            scaleMax = scaleMax,
            createdAt = Instant.now().toEpochMilli(),
        )
        val result = safeDbCall { habitDao.insert(entity) }
        if (result.isSuccess) pushHabitInBackground(entity)
        return result
    }

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> =
        habitCheckInDao.observeAll().map { entities -> entities.map { it.toDomain() } }

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
            )
        }
        val result = safeDbCall { habitCheckInDao.insertAll(entities) }
        if (result.isSuccess) pushCheckInsInBackground(entities)
        return result
    }

    override suspend fun updateCheckIn(
        habitId: String,
        date: LocalDate,
        value: Int,
    ): Result<Unit> {
        val existing = habitCheckInDao.getByHabitAndDate(habitId, date.toString())
            ?: return Result.failure(NoSuchElementException("Check-in for habit $habitId on $date not found"))
        val entity = existing.copy(value = value)
        val result = safeDbCall { habitCheckInDao.update(entity) }
        if (result.isSuccess) pushCheckInsInBackground(listOf(entity))
        return result
    }

    override suspend fun syncWithRemote(): Result<Unit> {
        val userId = authRepository.currentUserId() ?: return Result.success(Unit)
        return syncMutex.withLock {
            remoteCall {
                val localHabits = habitDao.getAll()
                remoteHabitDataSource.upsert(localHabits.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()
                val localCheckIns = habitCheckInDao.getAll()
                remoteHabitCheckInDataSource.upsert(localCheckIns.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()

                val remoteHabits = remoteHabitDataSource.fetchAll(userId).getOrThrow()
                remoteHabits.forEach { habitDao.upsert(it.toEntity()) }
                val remoteCheckIns = remoteHabitCheckInDataSource.fetchAll(userId).getOrThrow()
                habitCheckInDao.upsertAll(remoteCheckIns.map { it.toEntity() })
            }
        }
    }

    override suspend fun clearLocal(): Result<Unit> = safeDbCall {
        habitCheckInDao.clearAll()
        habitDao.clearAll()
    }

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
