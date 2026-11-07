package pl.luczka.todaywas.domain.onboarding.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.onboarding.repository.OnboardingRepository

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    suspend operator fun invoke(): Result<Unit> = repository.completeOnboarding()
}
