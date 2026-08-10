package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.model.JournalDateSlot
import java.time.LocalDate

class AddJournalEntryUseCaseTest {

    @Test
    fun `should resolve TODAY to LocalDate now`() =
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
    fun `should resolve YESTERDAY to LocalDate now minus one day`() =
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
