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
    fun `should make both slots addable when neither today nor yesterday is logged`() =
        runTest {
            // Arrange
            val useCase = ObserveAddableJournalDateSlotsUseCase(FakeJournalRepository())

            // Act
            val slots = useCase().first()

            // Assert
            assertEquals(setOf(JournalDateSlot.TODAY, JournalDateSlot.YESTERDAY), slots.toSet())
        }

    @Test
    fun `should make only yesterday addable when today is already logged`() =
        runTest {
            // Arrange
            val useCase = ObserveAddableJournalDateSlotsUseCase(
                FakeJournalRepository(listOf(entryFor(LocalDate.now()))),
            )

            // Act
            val slots = useCase().first()

            // Assert
            assertEquals(listOf(JournalDateSlot.YESTERDAY), slots)
        }

    @Test
    fun `should make no slots addable when both today and yesterday are logged`() =
        runTest {
            // Arrange
            val useCase = ObserveAddableJournalDateSlotsUseCase(
                FakeJournalRepository(listOf(entryFor(LocalDate.now()), entryFor(LocalDate.now().minusDays(1)))),
            )

            // Act
            val slots = useCase().first()

            // Assert
            assertEquals(emptyList<JournalDateSlot>(), slots)
        }
}
