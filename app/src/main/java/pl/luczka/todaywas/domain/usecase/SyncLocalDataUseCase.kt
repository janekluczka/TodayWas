package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.repository.HabitRepository
import pl.luczka.todaywas.domain.repository.JournalRepository
import javax.inject.Inject

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
