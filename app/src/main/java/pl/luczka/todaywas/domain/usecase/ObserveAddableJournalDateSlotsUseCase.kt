package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.domain.model.JournalDateSlot
import java.time.LocalDate
import javax.inject.Inject

class ObserveAddableJournalDateSlotsUseCase @Inject constructor(
    private val repository: JournalRepository,
) {

    operator fun invoke(): Flow<List<JournalDateSlot>> =
        repository
            .observeEntries()
            .map { entries ->
                val loggedDates = entries.map { it.date }.toSet()
                JournalDateSlot.entries.filter { it.toDate() !in loggedDates }
            }

    private fun JournalDateSlot.toDate(): LocalDate = when (this) {
        JournalDateSlot.TODAY -> LocalDate.now()
        JournalDateSlot.YESTERDAY -> LocalDate.now().minusDays(1)
    }
}
