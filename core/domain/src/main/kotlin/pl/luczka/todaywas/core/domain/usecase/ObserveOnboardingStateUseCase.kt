package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.core.domain.model.OnboardingState
import pl.luczka.todaywas.core.domain.repository.OnboardingRepository

class ObserveOnboardingStateUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    operator fun invoke(): Flow<OnboardingState> = repository.observeState()
}
