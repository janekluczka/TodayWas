package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.HabitRepository
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.data.repository.OnboardingRepository
import javax.inject.Inject

class ClearSyncedLocalDataUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val habitRepository: HabitRepository,
    private val onboardingRepository: OnboardingRepository,
) {

    suspend operator fun invoke(): Result<Unit> {
        val journalResult = journalRepository.clearLocal()
        val habitResult = habitRepository.clearLocal()
        val resetResult = onboardingRepository.resetSyncFlag()
        val failure = journalResult.exceptionOrNull() ?: habitResult.exceptionOrNull() ?: resetResult.exceptionOrNull()
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
