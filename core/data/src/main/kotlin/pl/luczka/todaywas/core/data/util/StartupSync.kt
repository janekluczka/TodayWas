package pl.luczka.todaywas.core.data.util

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pl.luczka.todaywas.core.domain.repository.OnboardingRepository
import pl.luczka.todaywas.domain.auth.model.AuthState
import pl.luczka.todaywas.domain.auth.repository.AuthRepository
import pl.luczka.todaywas.domain.entry.repository.SyncScheduler

// Basic reinstall/new-device restore: once per process, as soon as onboarding is complete and
// the user is signed in, enqueue a sync. Room stays the read source, so observing screens pick
// up whatever lands locally. Onboarding runs its own reviewed sync, hence the wait for it.
class StartupSync @Inject constructor(
    private val authRepository: AuthRepository,
    private val onboardingRepository: OnboardingRepository,
    private val syncScheduler: SyncScheduler,
) {

    suspend fun run() {
        onboardingRepository.observeState().first { it.completed }
        if (authRepository.observeAuthState().first { it !is AuthState.Loading } is AuthState.SignedIn) {
            syncScheduler.scheduleSync()
        }
    }
}
