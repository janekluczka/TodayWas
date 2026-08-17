package pl.luczka.todaywas.domain.repository

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
    var lastCreatedType: HabitType? = null
        private set
    var lastCreatedScaleMin: Int? = null
        private set
    var lastCreatedScaleMax: Int? = null
        private set

    var addCheckInsResult: Result<Unit> = Result.success(Unit)
    var lastLoggedDate: LocalDate? = null
        private set
    var lastLoggedValues: Map<String, Int>? = null
        private set

    var updateCheckInResult: Result<Unit> = Result.success(Unit)
    var updateCheckInCallCount = 0
        private set
    var lastUpdatedHabitId: String? = null
        private set
    var lastUpdatedDate: LocalDate? = null
        private set
    var lastUpdatedValue: Int? = null
        private set

    var deleteHabitResult: Result<Unit> = Result.success(Unit)
    var deleteHabitCallCount = 0
        private set
    var lastDeletedHabitId: String? = null
        private set

    var deleteCheckInResult: Result<Unit> = Result.success(Unit)
    var deleteCheckInCallCount = 0
        private set
    var lastDeletedCheckInHabitId: String? = null
        private set
    var lastDeletedCheckInDate: LocalDate? = null
        private set

    var syncWithRemoteResult: Result<Unit> = Result.success(Unit)
    var syncWithRemoteCallCount = 0
        private set

    var clearLocalResult: Result<Unit> = Result.success(Unit)
    var clearLocalCallCount = 0
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
        lastCreatedType = type
        lastCreatedScaleMin = scaleMin
        lastCreatedScaleMax = scaleMax
        return createHabitResult
    }

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> = checkInsFlow

    override suspend fun addCheckIns(
        date: LocalDate,
        values: Map<String, Int>,
    ): Result<Unit> {
        lastLoggedDate = date
        lastLoggedValues = values
        return addCheckInsResult
    }

    override suspend fun updateCheckIn(
        habitId: String,
        date: LocalDate,
        value: Int,
    ): Result<Unit> {
        updateCheckInCallCount++
        lastUpdatedHabitId = habitId
        lastUpdatedDate = date
        lastUpdatedValue = value
        return updateCheckInResult
    }

    override suspend fun deleteHabit(id: String): Result<Unit> {
        deleteHabitCallCount++
        lastDeletedHabitId = id
        return deleteHabitResult
    }

    override suspend fun deleteCheckIn(
        habitId: String,
        date: LocalDate,
    ): Result<Unit> {
        deleteCheckInCallCount++
        lastDeletedCheckInHabitId = habitId
        lastDeletedCheckInDate = date
        return deleteCheckInResult
    }

    override suspend fun syncWithRemote(): Result<Unit> {
        syncWithRemoteCallCount++
        return syncWithRemoteResult
    }

    override suspend fun clearLocal(): Result<Unit> {
        clearLocalCallCount++
        return clearLocalResult
    }
}
