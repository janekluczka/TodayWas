package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.local.HabitCheckInDao
import pl.luczka.todaywas.data.local.HabitCheckInEntity
import pl.luczka.todaywas.data.local.HabitDao
import pl.luczka.todaywas.data.local.HabitEntity
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao,
    private val habitCheckInDao: HabitCheckInDao,
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
            name = name,
            description = description,
            type = type.name,
            scaleMin = scaleMin,
            scaleMax = scaleMax,
            createdAt = Instant.now().toEpochMilli(),
        )
        return try {
            habitDao.insert(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retry once before giving up, per the plan's write-resilience contract.
            try {
                habitDao.insert(entity)
                Result.success(Unit)
            } catch (retryException: CancellationException) {
                throw retryException
            } catch (retryException: Exception) {
                Result.failure(retryException)
            }
        }
    }

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> =
        habitCheckInDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addCheckIns(
        date: LocalDate,
        values: Map<Long, Int>,
    ): Result<Unit> {
        val createdAt = Instant.now().toEpochMilli()
        val entities = values.map { (habitId, value) ->
            HabitCheckInEntity(
                habitId = habitId,
                date = date.toString(),
                value = value,
                createdAt = createdAt,
            )
        }
        return try {
            habitCheckInDao.insertAll(entities)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retry once before giving up, per the plan's write-resilience contract.
            try {
                habitCheckInDao.insertAll(entities)
                Result.success(Unit)
            } catch (retryException: CancellationException) {
                throw retryException
            } catch (retryException: Exception) {
                Result.failure(retryException)
            }
        }
    }
}
