package pl.luczka.todaywas.ui.preferences

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
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.ui.model.AuthStateUi

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {

    private fun viewModel(repository: FakeAuthRepository) =
        PreferencesViewModel(observeAuthState = ObserveAuthStateUseCase(repository))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects the observed auth state`() = runTest {
        val repository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))

        val viewModel = viewModel(repository)

        assertEquals(AuthStateUi.SignedIn(email = "a@b.com"), viewModel.uiState.value.authState)
    }

    @Test
    fun `AccountCardClicked emits NavigateToAccount`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        val events = mutableListOf<PreferencesUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onIntent(PreferencesIntent.AccountCardClicked)
        runCurrent()

        assertEquals(listOf(PreferencesUiEvent.NavigateToAccount), events)
        collectJob.cancel()
    }
}
