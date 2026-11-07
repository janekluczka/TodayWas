package pl.luczka.todaywas.domain.journal.usecase

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.core.testing.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.journal.model.JournalDateSlot

class AddJournalEntryUseCaseTest {

    @Test
    fun `should resolve the date to LocalDate now when the slot is TODAY`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            val useCase = AddJournalEntryUseCase(repository)

            // Act
            useCase(JournalDateSlot.TODAY, "Today was good.")

            // Assert
            assertEquals(LocalDate.now(), repository.lastSavedDate)
            assertEquals("Today was good.", repository.lastSavedText)
        }

    @Test
    fun `should resolve the date to LocalDate now minus one day when the slot is YESTERDAY`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            val useCase = AddJournalEntryUseCase(repository)

            // Act
            useCase(JournalDateSlot.YESTERDAY, "Yesterday was good.")

            // Assert
            assertEquals(LocalDate.now().minusDays(1), repository.lastSavedDate)
            assertEquals("Yesterday was good.", repository.lastSavedText)
        }
}
