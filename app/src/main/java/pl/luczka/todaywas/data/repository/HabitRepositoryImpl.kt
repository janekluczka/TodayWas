package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.luczka.todaywas.data.local.HabitCheckInDao
import pl.luczka.todaywas.data.local.HabitCheckInEntity
import pl.luczka.todaywas.data.local.HabitDao
import pl.luczka.todaywas.data.local.HabitEntity
import pl.luczka.todaywas.di.ApplicationScope
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
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
        return remoteCall {
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

    override suspend fun clearLocal(): Result<Unit> = safeDbCall {
        habitCheckInDao.clearAll()
        habitDao.clearAll()
    }

    // Best-effort - failures are silently swallowed since the local write already succeeded;
    // the next successful write (or the next sync pass) naturally retries via upsert.
    private fun pushHabitInBackground(entity: HabitEntity) {
        val userId = authRepository.currentUserId() ?: return
        syncScope.launch {
            runCatching { remoteHabitDataSource.upsert(listOf(entity.toDomain().toRemoteDto(userId))) }
        }
    }

    private fun pushCheckInsInBackground(entities: List<HabitCheckInEntity>) {
        val userId = authRepository.currentUserId() ?: return
        syncScope.launch {
            runCatching { remoteHabitCheckInDataSource.upsert(entities.map { it.toDomain().toRemoteDto(userId) }) }
        }
    }
}
