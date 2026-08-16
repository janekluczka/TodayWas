package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.first
import pl.luczka.todaywas.domain.model.LocalDataSummary
import pl.luczka.todaywas.domain.repository.HabitRepository
import pl.luczka.todaywas.domain.repository.JournalRepository
import javax.inject.Inject

class GetLocalDataSummaryUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val habitRepository: HabitRepository,
) {

    suspend operator fun invoke(): LocalDataSummary = LocalDataSummary(
        journalEntryCount = journalRepository.observeEntries().first().size,
        habitCount = habitRepository.observeHabits().first().size,
        checkInCount = habitRepository.observeCheckIns().first().size,
    )
}
