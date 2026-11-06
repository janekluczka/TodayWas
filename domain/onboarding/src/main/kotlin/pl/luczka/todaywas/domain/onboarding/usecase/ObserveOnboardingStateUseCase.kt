package pl.luczka.todaywas.domain.onboarding.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.onboarding.model.OnboardingState
import pl.luczka.todaywas.domain.onboarding.repository.OnboardingRepository

class ObserveOnboardingStateUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    operator fun invoke(): Flow<OnboardingState> = repository.observeState()
}
