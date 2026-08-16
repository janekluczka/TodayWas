package pl.luczka.todaywas.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.OnboardingState

interface OnboardingRepository {

    fun observeState(): Flow<OnboardingState>

    suspend fun completeOnboarding(): Result<Unit>

    suspend fun markLocalDataSynced(): Result<Unit>

    suspend fun resetSyncFlag(): Result<Unit>
}
