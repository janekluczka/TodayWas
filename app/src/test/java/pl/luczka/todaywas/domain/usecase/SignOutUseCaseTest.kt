package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.repository.FakeOnboardingRepository

class SignOutUseCaseTest {

    @Test
    fun `should clear local data and reset the sync flag when signed out successfully from a signed-in state`() =
        runTest {
            // Arrange
            val authRepository =
                FakeAuthRepository(
                    initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"),
                )
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val useCase =
                SignOutUseCase(
                    authRepository,
                    journalRepository,
                    habitRepository,
                    onboardingRepository,
                )

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, journalRepository.clearLocalCallCount)
            assertEquals(1, habitRepository.clearLocalCallCount)
            assertEquals(1, onboardingRepository.resetSyncFlagCallCount)
        }

    @Test
    fun `should not clear local data when signOut fails and the local session stays signed in`() =
        runTest {
            // Arrange
            val authRepository =
                FakeAuthRepository(
                    initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"),
                )
            authRepository.signOutError = AuthError.NetworkUnavailable
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val useCase =
                SignOutUseCase(
                    authRepository,
                    journalRepository,
                    habitRepository,
                    onboardingRepository,
                )

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, journalRepository.clearLocalCallCount)
            assertEquals(0, habitRepository.clearLocalCallCount)
            assertEquals(0, onboardingRepository.resetSyncFlagCallCount)
        }

    @Test
    fun `should not clear local data when already signed out before the call`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedOut)
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val useCase =
                SignOutUseCase(
                    authRepository,
                    journalRepository,
                    habitRepository,
                    onboardingRepository,
                )

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(0, journalRepository.clearLocalCallCount)
            assertEquals(0, habitRepository.clearLocalCallCount)
            assertEquals(0, onboardingRepository.resetSyncFlagCallCount)
        }
}
