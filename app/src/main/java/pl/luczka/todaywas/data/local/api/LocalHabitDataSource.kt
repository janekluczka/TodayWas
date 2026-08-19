package pl.luczka.todaywas.data.local.api

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import java.time.LocalDate

interface LocalHabitDataSource {

    fun observeHabits(): Flow<List<HabitEntity>>

    fun observeCheckIns(): Flow<List<HabitCheckInEntity>>

    suspend fun insertHabit(entity: HabitEntity): Result<Unit>

    suspend fun insertCheckIns(entities: List<HabitCheckInEntity>): Result<Unit>

    suspend fun updateCheckIn(
        habitId: String,
        date: LocalDate,
        value: Int,
        updatedAt: Long,
    ): Result<HabitCheckInEntity>

    suspend fun deleteHabitAndCheckIns(
        habitId: String,
        deletedAt: Long,
    ): Result<Pair<HabitEntity?, List<HabitCheckInEntity>>>

    suspend fun deleteCheckIn(
        habitId: String,
        date: LocalDate,
        deletedAt: Long,
    ): Result<HabitCheckInEntity>

    suspend fun getAllHabitsIncludingDeleted(): List<HabitEntity>

    suspend fun getAllCheckInsIncludingDeleted(): List<HabitCheckInEntity>

    suspend fun applyRemoteSnapshot(
        habitsToApply: List<HabitEntity>,
        checkInsToApply: List<HabitCheckInEntity>,
    )

    suspend fun purgeDeletedBefore(cutoff: Long)

    suspend fun clearAll(): Result<Unit>
}
