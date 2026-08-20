package pl.luczka.todaywas.ui.onboarding.allset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    fun `should emit Finished when GetStartedClicked`() =
        runTest {
            // Arrange
            val viewModel = viewModel(AllSetReason.NO_ACCOUNT)
            val events = mutableListOf<OnboardingAllSetUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAllSetIntent.GetStartedClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(OnboardingAllSetUiEvent.Finished), events)
            collectJob.cancel()
        }

    @Test
    fun `should emit Finished once the auto-advance delay elapses`() =
        runTest {
            // Arrange
            val viewModel = viewModel(AllSetReason.NO_ACCOUNT)
            val events = mutableListOf<OnboardingAllSetUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            testDispatcher.scheduler.advanceTimeBy(5_001)
            testDispatcher.scheduler.runCurrent()

            // Assert
            assertEquals(listOf(OnboardingAllSetUiEvent.Finished), events)
            collectJob.cancel()
        }

    @Test
    fun `should emit Finished only once when GetStartedClicked fires before the auto-advance delay elapses`() =
        runTest {
            // Arrange
            val viewModel = viewModel(AllSetReason.NO_ACCOUNT)
            val events = mutableListOf<OnboardingAllSetUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAllSetIntent.GetStartedClicked)
            testDispatcher.scheduler.advanceTimeBy(5_001)
            testDispatcher.scheduler.runCurrent()

            // Assert
            assertEquals(listOf(OnboardingAllSetUiEvent.Finished), events)
            collectJob.cancel()
        }
}
