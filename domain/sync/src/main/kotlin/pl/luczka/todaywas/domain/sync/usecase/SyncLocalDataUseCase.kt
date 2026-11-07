package pl.luczka.todaywas.domain.sync.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.habit.repository.HabitRepository
import pl.luczka.todaywas.domain.journal.repository.JournalRepository

class SyncLocalDataUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val habitRepository: HabitRepository,
) {

    suspend operator fun invoke(): Result<Unit> {
        val journalResult = journalRepository.syncWithRemote()
        val habitResult = habitRepository.syncWithRemote()
        val failure = journalResult.exceptionOrNull() ?: habitResult.exceptionOrNull()
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
