package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.repository.OnboardingRepository
import javax.inject.Inject

class ObserveOnboardingStateUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    operator fun invoke(): Flow<OnboardingState> = repository.observeState()
}
