package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.Instant
import java.time.LocalDate

class ObserveHabitCheckInBoardUseCaseTest {

    private val habit = Habit(
        id = 1L,
        name = "Drink water",
        description = null,
        type = HabitType.BINARY,
        scaleMin = null,
        scaleMax = null,
        createdAt = Instant.EPOCH,
    )

    private val checkIn = HabitCheckIn(
        id = 1L,
        habitId = 1L,
        date = LocalDate.of(2026, 7, 27),
        value = 1,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `combined flow reflects both sources' latest values`() =
        runTest {
            val repository = FakeHabitRepository()
            val useCase = ObserveHabitCheckInBoardUseCase(repository)

            assertEquals(0, useCase().first().habits.size)

            repository.habitsFlow.value = listOf(habit)
            assertEquals(listOf(habit), useCase().first().habits)
            assertEquals(0, useCase().first().checkIns.size)

            repository.checkInsFlow.value = listOf(checkIn)
            val board = useCase().first()
            assertEquals(listOf(habit), board.habits)
            assertEquals(listOf(checkIn), board.checkIns)
        }
}
