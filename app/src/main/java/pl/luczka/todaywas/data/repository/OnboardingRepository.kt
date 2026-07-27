package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState

interface OnboardingRepository {

    fun observeState(): Flow<OnboardingState>

    suspend fun saveFocus(focus: Focus): Result<Unit>
}
