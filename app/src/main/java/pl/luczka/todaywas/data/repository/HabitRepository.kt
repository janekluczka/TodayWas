package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.LocalDate

interface HabitRepository {

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
        values: Map<Long, Int>,
    ): Result<Unit>
}
