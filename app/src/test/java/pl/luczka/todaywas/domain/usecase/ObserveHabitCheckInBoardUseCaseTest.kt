package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import java.time.Instant
import java.time.LocalDate

class ObserveHabitCheckInBoardUseCaseTest {

    private val habit = Habit(
        id = "1",
        name = "Drink water",
        description = null,
        type = HabitType.BINARY,
        scaleMin = null,
        scaleMax = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val checkIn = HabitCheckIn(
        id = "1",
        habitId = "1",
        date = LocalDate.of(2026, 7, 27),
        value = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `should have no habits or check-ins when neither source has emitted`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val useCase = ObserveHabitCheckInBoardUseCase(repository)

            // Act
            val board = useCase().first()

            // Assert
            assertEquals(0, board.habits.size)
            assertEquals(0, board.checkIns.size)
        }

    @Test
    fun `should reflect habits with no check-ins when only habitsFlow has emitted`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val useCase = ObserveHabitCheckInBoardUseCase(repository)
            repository.habitsFlow.value = listOf(habit)

            // Act
            val board = useCase().first()

            // Assert
            assertEquals(listOf(habit), board.habits)
            assertEquals(0, board.checkIns.size)
        }

    @Test
    fun `should reflect both sources' latest values when both have emitted`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val useCase = ObserveHabitCheckInBoardUseCase(repository)
            repository.habitsFlow.value = listOf(habit)
            repository.checkInsFlow.value = listOf(checkIn)

            // Act
            val board = useCase().first()

            // Assert
            assertEquals(listOf(habit), board.habits)
            assertEquals(listOf(checkIn), board.checkIns)
        }
}
