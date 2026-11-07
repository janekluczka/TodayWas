package pl.luczka.todaywas.data.sync.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.core.testing.repository.FakeAuthRepository
import pl.luczka.todaywas.core.testing.repository.FakeOnboardingRepository
import pl.luczka.todaywas.domain.auth.model.AuthState
import pl.luczka.todaywas.domain.entry.repository.SyncScheduler
import pl.luczka.todaywas.domain.onboarding.model.OnboardingState

@OptIn(ExperimentalCoroutinesApi::class)
class StartupSyncTest {

    private class FakeSyncScheduler : SyncScheduler {

        var scheduleSyncCallCount = 0
            private set

        override fun scheduleSync() {
            scheduleSyncCallCount++
        }
    }

    private val signedIn = AuthState.SignedIn(
        userId = "u1",
        email = "a@b.com",
    )

    private fun onboarding(completed: Boolean) =
        FakeOnboardingRepository(
            OnboardingState(
                completed = completed,
                hasSyncedLocalData = false,
            ),
        )

    @Test
    fun `should schedule a sync when onboarding is complete and the user is signed in`() =
        runTest {
            // Arrange
            val scheduler = FakeSyncScheduler()
            val startupSync = StartupSync(FakeAuthRepository(signedIn), onboarding(completed = true), scheduler)

            // Act
            startupSync.run()

            // Assert
            assertEquals(1, scheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should not schedule a sync when the user is signed out`() =
        runTest {
            // Arrange
            val scheduler = FakeSyncScheduler()
            val startupSync =
                StartupSync(FakeAuthRepository(AuthState.SignedOut), onboarding(completed = true), scheduler)

            // Act
            startupSync.run()

            // Assert
            assertEquals(0, scheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should schedule a sync only once onboarding completes when it was incomplete at startup`() =
        runTest {
            // Arrange
            val scheduler = FakeSyncScheduler()
            val onboardingRepository = onboarding(completed = false)
            val startupSync = StartupSync(FakeAuthRepository(signedIn), onboardingRepository, scheduler)
            backgroundScope.launch { startupSync.run() }
            runCurrent()
            assertEquals(0, scheduler.scheduleSyncCallCount)

            // Act
            onboardingRepository.stateFlow.value = OnboardingState(
                completed = true,
                hasSyncedLocalData = false,
            )
            runCurrent()

            // Assert
            assertEquals(1, scheduler.scheduleSyncCallCount)
        }
}
