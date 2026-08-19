package pl.luczka.todaywas.ui.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitCheckInBoard
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import java.time.Instant
import java.time.LocalDate

class HabitMapperTest {

    private val today = LocalDate.of(2026, 6, 15)

    private fun habit(
        id: String = "1",
        type: HabitType = HabitType.BINARY,
    ) = Habit(
        id = id,
        name = "habit-$id",
        description = null,
        type = type,
        scaleMin = if (type == HabitType.SCALE) 1 else null,
        scaleMax = if (type == HabitType.SCALE) 5 else null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun checkIn(
        habitId: String,
        date: LocalDate,
        value: Int,
    ) = HabitCheckIn(
        id = habitId,
        habitId = habitId,
        date = date,
        value = value,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `should return NotLogged when no check-in exists for today`() {
        // Arrange
        val board = HabitCheckInBoard(habits = listOf(habit()), checkIns = emptyList())

        // Act
        val status = board.toHabitUiStates(today).single().todayStatus

        // Assert
        assertEquals(HabitCheckInStatusUiState.NotLogged, status)
    }

    @Test
    fun `should return LoggedBinary with done true when a BINARY habit's today check-in has value 1`() {
        // Arrange
        val board = HabitCheckInBoard(
            habits = listOf(habit(id = "1", type = HabitType.BINARY)),
            checkIns = listOf(checkIn(habitId = "1", date = today, value = 1)),
        )

        // Act
        val status = board.toHabitUiStates(today).single().todayStatus

        // Assert
        assertEquals(HabitCheckInStatusUiState.LoggedBinary(done = true), status)
    }

    @Test
    fun `should return LoggedScale with the check-in's value when habit type is SCALE`() {
        // Arrange
        val board = HabitCheckInBoard(
            habits = listOf(habit(id = "1", type = HabitType.SCALE)),
            checkIns = listOf(checkIn(habitId = "1", date = today, value = 3)),
        )

        // Act
        val status = board.toHabitUiStates(today).single().todayStatus

        // Assert
        assertEquals(HabitCheckInStatusUiState.LoggedScale(value = 3), status)
    }

    @Test
    fun `should ignore a check-in that is not dated today`() {
        // Arrange
        val board = HabitCheckInBoard(
            habits = listOf(habit(id = "1")),
            checkIns = listOf(checkIn(habitId = "1", date = today.minusDays(1), value = 1)),
        )

        // Act
        val status = board.toHabitUiStates(today).single().todayStatus

        // Assert
        assertEquals(HabitCheckInStatusUiState.NotLogged, status)
    }
}
