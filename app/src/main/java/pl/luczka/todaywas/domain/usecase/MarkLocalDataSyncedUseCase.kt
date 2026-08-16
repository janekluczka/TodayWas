package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.repository.OnboardingRepository
import javax.inject.Inject

class MarkLocalDataSyncedUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    suspend operator fun invoke(): Result<Unit> = repository.markLocalDataSynced()
}
