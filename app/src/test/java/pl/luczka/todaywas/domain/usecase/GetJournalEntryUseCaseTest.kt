package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate

class GetJournalEntryUseCaseTest {

    @Test
    fun `returns the entry when found`() =
        runTest {
            val entry = JournalEntry(
                id = 1L,
                date = LocalDate.of(2026, 7, 27),
                text = "Today was good.",
                createdAt = Instant.EPOCH,
            )
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val useCase = GetJournalEntryUseCase(repository)

            val result = useCase(1L)

            assertEquals(entry, result)
        }

    @Test
    fun `returns null when not found`() =
        runTest {
            val repository = FakeJournalRepository()
            val useCase = GetJournalEntryUseCase(repository)

            val result = useCase(1L)

            assertNull(result)
        }
}
