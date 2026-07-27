package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.Focus
import javax.inject.Inject

class SelectFocusUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {

    suspend operator fun invoke(focus: Focus): Result<Unit> = repository.saveFocus(focus)
}
