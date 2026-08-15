package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.first
import pl.luczka.todaywas.data.repository.AuthRepository
import pl.luczka.todaywas.data.repository.HabitRepository
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.AuthState
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val journalRepository: JournalRepository,
    private val habitRepository: HabitRepository,
    private val onboardingRepository: OnboardingRepository,
) {

    // Clearing is gated on the actual SignedIn->SignedOut auth-state transition around the call,
    // not signOut()'s own Result, so every sign-out entry point clears synced local data the same
    // way a local session actually ends, whether or not the remote revoke call itself succeeds.
    suspend operator fun invoke(): Result<Unit> {
        val wasSignedIn = authRepository.observeAuthState().first() is AuthState.SignedIn
        val result = authRepository.signOut()
        val isNowSignedOut = authRepository.observeAuthState().first() is AuthState.SignedOut
        if (wasSignedIn && isNowSignedOut) {
            journalRepository.clearLocal()
            habitRepository.clearLocal()
            onboardingRepository.resetSyncFlag()
        }
        return result
    }
}
