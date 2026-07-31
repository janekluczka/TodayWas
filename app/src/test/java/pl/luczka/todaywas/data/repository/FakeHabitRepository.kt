package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.LocalDate

class FakeHabitRepository(
    initialHabits: List<Habit> = emptyList(),
    initialCheckIns: List<HabitCheckIn> = emptyList(),
) : HabitRepository {

    val habitsFlow = MutableStateFlow(initialHabits)
    val checkInsFlow = MutableStateFlow(initialCheckIns)

    var createHabitResult: Result<Unit> = Result.success(Unit)
    var createHabitCallCount = 0
        private set

    var addCheckInsResult: Result<Unit> = Result.success(Unit)
    var lastLoggedDate: LocalDate? = null
        private set
    var lastLoggedValues: Map<Long, Int>? = null
        private set

    override fun observeHabits(): Flow<List<Habit>> = habitsFlow

    override suspend fun createHabit(
        name: String,
        description: String?,
        type: HabitType,
        scaleMin: Int?,
        scaleMax: Int?,
    ): Result<Unit> {
        createHabitCallCount++
        return createHabitResult
    }

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> = checkInsFlow

    override suspend fun addCheckIns(
        date: LocalDate,
        values: Map<Long, Int>,
    ): Result<Unit> {
        lastLoggedDate = date
        lastLoggedValues = values
        return addCheckInsResult
    }
}
