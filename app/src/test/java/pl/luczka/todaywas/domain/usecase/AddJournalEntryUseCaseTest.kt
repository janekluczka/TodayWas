package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.LocalDate

class AddJournalEntryUseCaseTest {

    private class FakeRepository : JournalRepository {

        var lastSavedDate: LocalDate? = null
            private set
        var lastSavedText: String? = null
            private set

        override fun observeEntries(): Flow<List<JournalEntry>> = flowOf(emptyList())

        override suspend fun addEntry(
            date: LocalDate,
            text: String,
        ): Result<Unit> {
            lastSavedDate = date
            lastSavedText = text
            return Result.success(Unit)
        }
    }

    @Test
    fun `TODAY resolves to LocalDate now`() =
        runTest {
            val repository = FakeRepository()
            val useCase = AddJournalEntryUseCase(repository)

            useCase(JournalDateSlot.TODAY, "Today was good.")

            assertEquals(LocalDate.now(), repository.lastSavedDate)
            assertEquals("Today was good.", repository.lastSavedText)
        }

    @Test
    fun `YESTERDAY resolves to LocalDate now minus one day`() =
        runTest {
            val repository = FakeRepository()
            val useCase = AddJournalEntryUseCase(repository)

            useCase(JournalDateSlot.YESTERDAY, "Yesterday was good.")

            assertEquals(LocalDate.now().minusDays(1), repository.lastSavedDate)
            assertEquals("Yesterday was good.", repository.lastSavedText)
        }
}
