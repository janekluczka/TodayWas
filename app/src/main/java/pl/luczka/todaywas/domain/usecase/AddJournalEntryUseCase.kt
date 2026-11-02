package pl.luczka.todaywas.domain.usecase

import java.time.LocalDate
import javax.inject.Inject
import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.repository.JournalRepository

class AddJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {

    suspend operator fun invoke(
        slot: JournalDateSlot,
        text: String,
    ): Result<Unit> {
        val date = when (slot) {
            JournalDateSlot.TODAY -> LocalDate.now()
            JournalDateSlot.YESTERDAY -> LocalDate.now().minusDays(1)
        }
        return repository.addEntry(date, text)
    }
}
