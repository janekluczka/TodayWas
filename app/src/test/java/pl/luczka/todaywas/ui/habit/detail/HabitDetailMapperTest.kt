package pl.luczka.todaywas.ui.habit.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.HabitCheckIn
import java.time.Instant
import java.time.LocalDate

class HabitDetailMapperTest {

    private val today = LocalDate.of(2026, 6, 15)
    private val yesterday = today.minusDays(1)
    private val freshLoggableDates = listOf(today, yesterday)

    private fun checkIn(
        date: LocalDate,
        value: Int,
        createdAt: Instant,
    ) = HabitCheckIn(
        id = "1",
        habitId = "1",
        date = date,
        value = value,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `should return eligibleForEdit true and alreadyLogged false when today is not yet logged`() {
        // Act
        val day = emptyList<HabitCheckIn>()
            .toSelectedDayUiState(today, freshLoggableDates, isEditable = { true })

        // Assert
        assertEquals(today, day.date)
        assertEquals(null, day.value)
        assertFalse(day.alreadyLogged)
        assertTrue(day.eligibleForEdit)
    }

    @Test
    fun `should return eligibleForEdit true and alreadyLogged true when a logged day is within the edit window`() {
        // Arrange
        val checkIns = listOf(checkIn(today, value = 1, createdAt = Instant.EPOCH))

        // Act
        val day = checkIns.toSelectedDayUiState(today, freshLoggableDates, isEditable = { true })

        // Assert
        assertEquals(1, day.value)
        assertTrue(day.alreadyLogged)
        assertTrue(day.eligibleForEdit)
    }

    @Test
    fun `should return eligibleForEdit false and alreadyLogged true when a logged day is past the edit window`() {
        // Arrange
        val checkIns = listOf(checkIn(today, value = 0, createdAt = Instant.EPOCH))

        // Act
        val day = checkIns.toSelectedDayUiState(today, freshLoggableDates, isEditable = { false })

        // Assert
        assertTrue(day.alreadyLogged)
        assertFalse(day.eligibleForEdit)
    }

    @Test
    fun `should return eligibleForEdit false and alreadyLogged false when an older unlogged day is selected`() {
        // Arrange
        val oldDate = today.minusDays(10)

        // Act
        val day = emptyList<HabitCheckIn>()
            .toSelectedDayUiState(oldDate, freshLoggableDates, isEditable = { true })

        // Assert
        assertFalse(day.alreadyLogged)
        assertFalse(day.eligibleForEdit)
    }

    @Test
    fun `should return eligibleForEdit true and alreadyLogged false when yesterday is not yet logged`() {
        // Act
        val day = emptyList<HabitCheckIn>()
            .toSelectedDayUiState(yesterday, freshLoggableDates, isEditable = { true })

        // Assert
        assertFalse(day.alreadyLogged)
        assertTrue(day.eligibleForEdit)
    }
}
