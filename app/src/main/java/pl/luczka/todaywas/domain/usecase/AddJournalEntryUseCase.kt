package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.domain.model.JournalDateSlot
import java.time.LocalDate
import javax.inject.Inject

class AddJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {

    suspend operator fun invoke(
        slot: JournalDateSlot,
        text: String,
    ): Result<Unit> {
        val date =
            when (slot) {
                JournalDateSlot.TODAY -> LocalDate.now()
                JournalDateSlot.YESTERDAY -> LocalDate.now().minusDays(1)
            }
        return repository.addEntry(date, text)
    }
}
