package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import java.time.Instant
import java.time.LocalDate

class GetJournalEntryUseCaseTest {

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
            val useCase = GetJournalEntryUseCase(repository)

            // Act
            val result = useCase("1")

            // Assert
            assertEquals(entry, result)
        }

    @Test
    fun `should return null when not found`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            val useCase = GetJournalEntryUseCase(repository)

            // Act
            val result = useCase("1")

            // Assert
            assertNull(result)
        }
}
