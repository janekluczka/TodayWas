package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.core.domain.model.JournalEntry
import pl.luczka.todaywas.core.domain.repository.JournalRepository

class ObserveJournalEntriesUseCase @Inject constructor(
    private val repository: JournalRepository,
) {

    operator fun invoke(): Flow<List<JournalEntry>> = repository.observeEntries()
}
