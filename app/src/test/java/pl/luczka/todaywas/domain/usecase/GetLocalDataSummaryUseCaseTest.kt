package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import java.time.Instant
import java.time.LocalDate

class GetLocalDataSummaryUseCaseTest {

    @Test
    fun `should return all-zero counts when both repositories are empty`() =
        runTest {
            // Arrange
            val useCase = GetLocalDataSummaryUseCase(FakeJournalRepository(), FakeHabitRepository())

            // Act
            val summary = useCase()

            // Assert
            assertEquals(0, summary.journalEntryCount)
            assertEquals(0, summary.habitCount)
            assertEquals(0, summary.checkInCount)
            assertEquals(true, summary.isEmpty)
        }

    @Test
    fun `should count entries, habits, and check-ins from both repositories`() =
        runTest {
            // Arrange
            val entry = JournalEntry(
                id = "1",
                date = LocalDate.of(2026, 8, 1),
                text = "text",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val habit = Habit(
                id = "1",
                name = "Drink water",
                description = null,
                type = HabitType.BINARY,
                scaleMin = null,
                scaleMax = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val checkIn = HabitCheckIn(
                id = "1",
                habitId = "1",
                date = LocalDate.of(2026, 8, 1),
                value = 1,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry))
            val habitRepository =
                FakeHabitRepository(
                    initialHabits = listOf(habit),
                    initialCheckIns = listOf(checkIn),
                )
            val useCase = GetLocalDataSummaryUseCase(journalRepository, habitRepository)

            // Act
            val summary = useCase()

            // Assert
            assertEquals(1, summary.journalEntryCount)
            assertEquals(1, summary.habitCount)
            assertEquals(1, summary.checkInCount)
            assertEquals(false, summary.isEmpty)
        }
}
