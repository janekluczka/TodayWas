package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate

class ObserveAddableJournalDateSlotsUseCaseTest {

    private fun entryFor(date: LocalDate) = JournalEntry(
        id = 1L,
        date = date,
        text = "text",
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `both slots addable when neither today nor yesterday is logged`() =
        runTest {
            val useCase = ObserveAddableJournalDateSlotsUseCase(FakeJournalRepository())

            val slots = useCase().first()

            assertEquals(setOf(JournalDateSlot.TODAY, JournalDateSlot.YESTERDAY), slots.toSet())
        }

    @Test
    fun `only yesterday addable when today is already logged`() =
        runTest {
            val useCase = ObserveAddableJournalDateSlotsUseCase(
                FakeJournalRepository(listOf(entryFor(LocalDate.now()))),
            )

            val slots = useCase().first()

            assertEquals(listOf(JournalDateSlot.YESTERDAY), slots)
        }

    @Test
    fun `no slots addable when both today and yesterday are logged`() =
        runTest {
            val useCase = ObserveAddableJournalDateSlotsUseCase(
                FakeJournalRepository(listOf(entryFor(LocalDate.now()), entryFor(LocalDate.now().minusDays(1)))),
            )

            val slots = useCase().first()

            assertEquals(emptyList<JournalDateSlot>(), slots)
        }
}
