package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pl.luczka.todaywas.domain.model.OnboardingState

class FakeOnboardingRepository(
    initialState: OnboardingState = OnboardingState(completed = false, hasSyncedLocalData = false),
) : OnboardingRepository {

    val stateFlow = MutableStateFlow(initialState)

    var completeOnboardingResult: Result<Unit> = Result.success(Unit)
    var completeOnboardingCallCount = 0
        private set

    var markLocalDataSyncedResult: Result<Unit> = Result.success(Unit)
    var markLocalDataSyncedCallCount = 0
        private set

    var resetSyncFlagResult: Result<Unit> = Result.success(Unit)
    var resetSyncFlagCallCount = 0
        private set

    override fun observeState(): Flow<OnboardingState> = stateFlow

    override suspend fun completeOnboarding(): Result<Unit> {
        completeOnboardingCallCount++
        return completeOnboardingResult
    }

    override suspend fun markLocalDataSynced(): Result<Unit> {
        markLocalDataSyncedCallCount++
        return markLocalDataSyncedResult
    }

    override suspend fun resetSyncFlag(): Result<Unit> {
        resetSyncFlagCallCount++
        return resetSyncFlagResult
    }
}
