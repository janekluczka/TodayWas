package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.model.JournalDateSlot
import java.time.LocalDate

class AddJournalEntryUseCaseTest {

    @Test
    fun `TODAY resolves to LocalDate now`() =
        runTest {
            val repository = FakeJournalRepository()
            val useCase = AddJournalEntryUseCase(repository)

            useCase(JournalDateSlot.TODAY, "Today was good.")

            assertEquals(LocalDate.now(), repository.lastSavedDate)
            assertEquals("Today was good.", repository.lastSavedText)
        }

    @Test
    fun `YESTERDAY resolves to LocalDate now minus one day`() =
        runTest {
            val repository = FakeJournalRepository()
            val useCase = AddJournalEntryUseCase(repository)

            useCase(JournalDateSlot.YESTERDAY, "Yesterday was good.")

            assertEquals(LocalDate.now().minusDays(1), repository.lastSavedDate)
            assertEquals("Yesterday was good.", repository.lastSavedText)
        }
}
