package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState

class FakeOnboardingRepository(
    initialState: OnboardingState = OnboardingState(completed = false, focus = null, hasSyncedLocalData = false),
) : OnboardingRepository {

    val stateFlow = MutableStateFlow(initialState)

    var saveFocusResult: Result<Unit> = Result.success(Unit)
    var saveFocusCallCount = 0
        private set

    var markLocalDataSyncedResult: Result<Unit> = Result.success(Unit)
    var markLocalDataSyncedCallCount = 0
        private set

    var resetSyncFlagResult: Result<Unit> = Result.success(Unit)
    var resetSyncFlagCallCount = 0
        private set

    override fun observeState(): Flow<OnboardingState> = stateFlow

    override suspend fun saveFocus(focus: Focus): Result<Unit> {
        saveFocusCallCount++
        return saveFocusResult
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
