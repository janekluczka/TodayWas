package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.domain.model.JournalEntry
import javax.inject.Inject

class ObserveJournalEntriesUseCase @Inject constructor(
    private val repository: JournalRepository,
) {

    operator fun invoke(): Flow<List<JournalEntry>> = repository.observeEntries()
}
