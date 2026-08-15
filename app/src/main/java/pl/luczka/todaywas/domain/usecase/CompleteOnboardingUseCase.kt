package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.OnboardingRepository
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    suspend operator fun invoke(): Result<Unit> = repository.completeOnboarding()
}
