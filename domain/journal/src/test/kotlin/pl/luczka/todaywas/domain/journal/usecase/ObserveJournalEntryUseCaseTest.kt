package pl.luczka.todaywas.domain.journal.usecase

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.luczka.todaywas.core.testing.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.journal.model.JournalEntry

class ObserveJournalEntryUseCaseTest {

    @Test
    fun `should return the entry when found`() =
        runTest {
            // Arrange
            val entry = JournalEntry(
                id = "1",
                date = LocalDate.of(2026, 7, 27),
                text = "Today was good.",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val useCase = ObserveJournalEntryUseCase(repository)

            // Act
            val result = useCase("1").first()

            // Assert
            assertEquals(entry, result)
        }

    @Test
    fun `should return null when not found`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            val useCase = ObserveJournalEntryUseCase(repository)

            // Act
            val result = useCase("1").first()

            // Assert
            assertNull(result)
        }
}
