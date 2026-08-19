package pl.luczka.todaywas.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.LocalDate

interface HabitRepository : Syncable {

    fun observeHabits(): Flow<List<Habit>>

    suspend fun createHabit(
        name: String,
        description: String?,
        type: HabitType,
        scaleMin: Int?,
        scaleMax: Int?,
    ): Result<Unit>

    fun observeCheckIns(): Flow<List<HabitCheckIn>>

    suspend fun addCheckIns(
        date: LocalDate,
        values: Map<String, Int>,
    ): Result<Unit>

    // Callers must check EditWindow.isEditable first - enforced by UpdateHabitCheckInUseCase,
    // not here.
    suspend fun updateCheckIn(
        habitId: String,
        date: LocalDate,
        value: Int,
    ): Result<Unit>

    suspend fun deleteHabit(id: String): Result<Unit>

    suspend fun deleteCheckIn(
        habitId: String,
        date: LocalDate,
    ): Result<Unit>
}
