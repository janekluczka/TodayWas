package pl.luczka.todaywas.data.local.api

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.dao.HabitCheckInDao
import pl.luczka.todaywas.data.local.dao.HabitDao
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.util.TransactionRunner
import pl.luczka.todaywas.data.util.safeDbCall
import java.time.LocalDate
import javax.inject.Inject

class LocalHabitDataSourceImpl @Inject constructor(
    private val habitDao: HabitDao,
    private val habitCheckInDao: HabitCheckInDao,
    private val transactionRunner: TransactionRunner,
) : LocalHabitDataSource {

    override fun observeHabits(): Flow<List<HabitEntity>> = habitDao.observeAll()

    override fun observeCheckIns(): Flow<List<HabitCheckInEntity>> = habitCheckInDao.observeAll()

    override suspend fun insertHabit(entity: HabitEntity): Result<Unit> = safeDbCall { habitDao.insert(entity) }

    override suspend fun insertCheckIns(entities: List<HabitCheckInEntity>): Result<Unit> =
        safeDbCall { habitCheckInDao.insertAll(entities) }

    override suspend fun updateCheckIn(
        habitId: String,
        date: LocalDate,
        value: Int,
        updatedAt: Long,
    ): Result<HabitCheckInEntity> {
        val existing = habitCheckInDao.getByHabitAndDate(habitId, date.toString())
            ?: return Result.failure(NoSuchElementException("Check-in for habit $habitId on $date not found"))
        val entity = existing.copy(value = value, updatedAt = updatedAt)
        return safeDbCall { habitCheckInDao.update(entity) }.map { entity }
    }

    override suspend fun deleteHabitAndCheckIns(
        habitId: String,
        deletedAt: Long,
    ): Result<Pair<HabitEntity?, List<HabitCheckInEntity>>> {
        val existingHabit = habitDao.getById(habitId)
        val existingCheckIns = habitCheckInDao.getByHabitId(habitId)
        // Transactional so a habit is never left with only some of its check-ins deleted (or
        // vice versa) if the second delete fails - safeDbCall's retry re-runs the whole
        // transaction, not just the failed half.
        val result = safeDbCall {
            transactionRunner.runInTransaction {
                habitCheckInDao.softDeleteByHabitId(habitId, deletedAt)
                habitDao.softDeleteById(habitId, deletedAt)
            }
        }
        return result.map {
            existingHabit?.copy(deletedAt = deletedAt) to existingCheckIns.map { it.copy(deletedAt = deletedAt) }
        }
    }

    override suspend fun deleteCheckIn(
        habitId: String,
        date: LocalDate,
        deletedAt: Long,
    ): Result<HabitCheckInEntity> {
        val existing = habitCheckInDao.getByHabitAndDate(habitId, date.toString())
            ?: return Result.failure(NoSuchElementException("Check-in for habit $habitId on $date not found"))
        return safeDbCall { habitCheckInDao.softDeleteById(existing.id, deletedAt) }.map { existing.copy(deletedAt = deletedAt) }
    }

    override suspend fun getAllHabitsIncludingDeleted(): List<HabitEntity> = habitDao.getAllIncludingDeleted()

    override suspend fun getAllCheckInsIncludingDeleted(): List<HabitCheckInEntity> = habitCheckInDao.getAllIncludingDeleted()

    override suspend fun applyRemoteSnapshot(
        habitsToApply: List<HabitEntity>,
        checkInsToApply: List<HabitCheckInEntity>,
    ) {
        transactionRunner.runInTransaction {
            habitsToApply.forEach { habitDao.upsert(it) }
            habitCheckInDao.upsertAll(checkInsToApply)
        }
    }

    override suspend fun purgeDeletedBefore(cutoff: Long) {
        habitDao.purgeDeletedBefore(cutoff)
        habitCheckInDao.purgeDeletedBefore(cutoff)
    }

    override suspend fun clearAll(): Result<Unit> = safeDbCall {
        habitCheckInDao.clearAll()
        habitDao.clearAll()
    }
}
