package pl.luczka.todaywas.ui.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitCheckInBoard
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.util.HabitContributionCalculator
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitSortUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HabitMapperTest {

    private val today = LocalDate.of(2026, 6, 15)
    private val now = today.atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun habit(
        id: String = "1",
        type: HabitType = HabitType.BINARY,
        name: String = "habit-$id",
        createdAt: Instant = Instant.EPOCH,
    ) = Habit(
        id = id,
        name = name,
        description = null,
        type = type,
        scaleMin = if (type == HabitType.SCALE) 1 else null,
        scaleMax = if (type == HabitType.SCALE) 5 else null,
        createdAt = createdAt,
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

    private fun HabitCheckInBoard.sorted(sort: HabitSortUiState) =
        toSortedHabitUiStates(today, now, sort)

    @Test
    fun `should return NotLogged when no check-in exists for today`() {
        // Arrange
        val board = HabitCheckInBoard(habits = listOf(habit()), checkIns = emptyList())

        // Act
        val status = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).single().todayStatus

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
        val status = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).single().todayStatus

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
        val status = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).single().todayStatus

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
        val status = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).single().todayStatus

        // Assert
        assertEquals(HabitCheckInStatusUiState.NotLogged, status)
    }

    @Test
    fun `should return NONE for todayLevel when the habit has no check-ins`() {
        // Arrange
        val board = HabitCheckInBoard(habits = listOf(habit()), checkIns = emptyList())

        // Act
        val level = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).single().todayLevel

        // Assert
        assertEquals(DsContributionLevel.NONE, level)
    }

    @Test
    fun `should compute todayLevel matching HabitContributionCalculator's own output`() {
        // Arrange
        val checkIns = listOf(
            checkIn(habitId = "1", date = today.minusDays(1), value = 1),
            checkIn(habitId = "1", date = today, value = 5),
        )
        val board = HabitCheckInBoard(habits = listOf(habit()), checkIns = checkIns)
        val expectedLevel = HabitContributionCalculator
            .compute(checkIns, ContributionWindow.RollingTwelveMonths, now)
            .days
            .getValue(today)

        // Act
        val level = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).single().todayLevel

        // Assert
        assertEquals(expectedLevel.toUiState(), level)
    }

    @Test
    fun `should sort RECENTLY_CHECKED_IN by most recent check-in date descending`() {
        // Arrange
        val board = HabitCheckInBoard(
            habits = listOf(habit(id = "1"), habit(id = "2"), habit(id = "3")),
            checkIns = listOf(
                checkIn(habitId = "1", date = today.minusDays(5), value = 1),
                checkIn(habitId = "2", date = today.minusDays(1), value = 1),
                checkIn(habitId = "3", date = today.minusDays(10), value = 1),
            ),
        )

        // Act
        val ids = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).map { it.id }

        // Assert
        assertEquals(listOf("2", "1", "3"), ids)
    }

    @Test
    fun `should sort habits with zero check-ins ever last, tiebroken by createdAt ascending`() {
        // Arrange
        val board = HabitCheckInBoard(
            habits = listOf(
                habit(id = "1", createdAt = Instant.ofEpochSecond(200)),
                habit(id = "2", createdAt = Instant.ofEpochSecond(100)),
                habit(id = "3"),
            ),
            checkIns = listOf(checkIn(habitId = "3", date = today, value = 1)),
        )

        // Act
        val ids = board.sorted(HabitSortUiState.RECENTLY_CHECKED_IN).map { it.id }

        // Assert
        assertEquals(listOf("3", "2", "1"), ids)
    }

    @Test
    fun `should sort ALPHABETICAL by name ascending`() {
        // Arrange
        val board = HabitCheckInBoard(
            habits = listOf(
                habit(id = "1", name = "Run"),
                habit(id = "2", name = "Drink water"),
                habit(id = "3", name = "Meditate"),
            ),
            checkIns = emptyList(),
        )

        // Act
        val names = board.sorted(HabitSortUiState.ALPHABETICAL).map { it.name }

        // Assert
        assertEquals(listOf("Drink water", "Meditate", "Run"), names)
    }

    @Test
    fun `should sort DATE_CREATED by createdAt ascending`() {
        // Arrange
        val board = HabitCheckInBoard(
            habits = listOf(
                habit(id = "1", createdAt = Instant.ofEpochSecond(300)),
                habit(id = "2", createdAt = Instant.ofEpochSecond(100)),
                habit(id = "3", createdAt = Instant.ofEpochSecond(200)),
            ),
            checkIns = emptyList(),
        )

        // Act
        val ids = board.sorted(HabitSortUiState.DATE_CREATED).map { it.id }

        // Assert
        assertEquals(listOf("2", "3", "1"), ids)
    }
}
