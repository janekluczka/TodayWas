package pl.luczka.todaywas.data.repository

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
        return safeDbCall { habitDao.insert(entity) }
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
        return safeDbCall { habitCheckInDao.insertAll(entities) }
    }

    override suspend fun updateCheckIn(
        habitId: Long,
        date: LocalDate,
        value: Int,
    ): Result<Unit> {
        val existing = habitCheckInDao.getByHabitAndDate(habitId, date.toString())
            ?: return Result.failure(NoSuchElementException("Check-in for habit $habitId on $date not found"))
        val entity = existing.copy(value = value)
        return safeDbCall { habitCheckInDao.update(entity) }
    }
}
