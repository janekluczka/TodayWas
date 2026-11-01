package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.repository.OnboardingRepository

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    suspend operator fun invoke(): Result<Unit> = repository.completeOnboarding()
}
