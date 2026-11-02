package pl.luczka.todaywas.ui.onboarding.allset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.ui.onboarding.AllSetReason

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingAllSetViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun viewModel(
        reason: AllSetReason,
        authRepository: FakeAuthRepository = FakeAuthRepository(),
    ) = OnboardingAllSetViewModel(
        reason = reason,
        observeAuthState = ObserveAuthStateUseCase(authRepository),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose the given reason and no email on initial state`() =
        runTest {
            // Arrange & Act
            val viewModel = viewModel(AllSetReason.NO_ACCOUNT)

            // Assert
            val state = viewModel.uiState.value
            assertEquals(AllSetReason.NO_ACCOUNT, state.reason)
            assertNull(state.email)
            assertNull(state.pendingNavigation)
        }

    @Test
    fun `should expose the signed-in email when reason is SIGNED_IN`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(
                initialState = AuthState.SignedIn(userId = "u1", email = "person@example.com"),
            )

            // Act
            val viewModel = viewModel(AllSetReason.SIGNED_IN, authRepository)

            // Assert
            assertEquals("person@example.com", viewModel.uiState.value.email)
        }

    @Test
    fun `should set Finished when GetStartedClicked`() =
        runTest {
            // Arrange
            val viewModel = viewModel(AllSetReason.NO_ACCOUNT)

            // Act
            viewModel.onIntent(OnboardingAllSetIntent.GetStartedClicked)

            // Assert
            assertEquals(OnboardingAllSetNavigation.Finished, viewModel.uiState.value.pendingNavigation)
        }

    @Test
    fun `should set Finished once the auto-advance delay elapses`() =
        runTest {
            // Arrange
            val viewModel = viewModel(AllSetReason.NO_ACCOUNT)

            // Act
            testDispatcher.scheduler.advanceTimeBy(5_001)
            testDispatcher.scheduler.runCurrent()

            // Assert
            assertEquals(OnboardingAllSetNavigation.Finished, viewModel.uiState.value.pendingNavigation)
        }

    @Test
    fun `should not navigate again when the auto-advance delay elapses after a handled GetStartedClicked`() =
        runTest {
            // Arrange
            val viewModel = viewModel(AllSetReason.NO_ACCOUNT)
            viewModel.onIntent(OnboardingAllSetIntent.GetStartedClicked)
            viewModel.onIntent(OnboardingAllSetIntent.NavigationHandled)

            // Act
            testDispatcher.scheduler.advanceTimeBy(5_001)
            testDispatcher.scheduler.runCurrent()

            // Assert
            assertNull(viewModel.uiState.value.pendingNavigation)
        }
}
