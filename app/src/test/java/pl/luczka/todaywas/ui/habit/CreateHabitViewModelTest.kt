package pl.luczka.todaywas.ui.habit

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.usecase.CreateHabitUseCase
import pl.luczka.todaywas.ui.model.HabitTypeUiState

@OptIn(ExperimentalCoroutinesApi::class)
class CreateHabitViewModelTest {

    private fun viewModel(repository: FakeHabitRepository = FakeHabitRepository()) =
        CreateHabitViewModel(CreateHabitUseCase(repository))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is blank and defaults to BINARY`() =
        runTest {
            val viewModel = viewModel()

            val state = viewModel.uiState.value

            assertEquals("", state.name)
            assertEquals("", state.description)
            assertEquals(HabitTypeUiState.BINARY, state.type)
            assertFalse(state.isSaving)
            assertFalse(state.saveError)
        }

    @Test
    fun `NameChanged updates name`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(CreateHabitIntent.NameChanged("Drink water"))

            assertEquals("Drink water", viewModel.uiState.value.name)
        }

    @Test
    fun `TypeChanged updates type`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(CreateHabitIntent.TypeChanged(HabitTypeUiState.SCALE))

            assertEquals(HabitTypeUiState.SCALE, viewModel.uiState.value.type)
        }

    @Test
    fun `ScaleMinChanged and ScaleMaxChanged update their fields`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(CreateHabitIntent.ScaleMinChanged("1"))
            viewModel.onIntent(CreateHabitIntent.ScaleMaxChanged("5"))

            assertEquals("1", viewModel.uiState.value.scaleMin)
            assertEquals("5", viewModel.uiState.value.scaleMax)
        }

    @Test
    fun `SaveClicked success passes parsed fields to the use case and emits Saved`() =
        runTest {
            val repository = FakeHabitRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(CreateHabitIntent.NameChanged("Drink water"))
            viewModel.onIntent(CreateHabitIntent.TypeChanged(HabitTypeUiState.SCALE))
            viewModel.onIntent(CreateHabitIntent.ScaleMinChanged("1"))
            viewModel.onIntent(CreateHabitIntent.ScaleMaxChanged("5"))
            val events = mutableListOf<CreateHabitUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(CreateHabitIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.createHabitCallCount)
            assertEquals(listOf(CreateHabitUiEvent.Saved), events)
            collectJob.cancel()
        }

    @Test
    fun `SaveClicked failure sets saveError and does not emit Saved`() =
        runTest {
            val repository = FakeHabitRepository()
            repository.createHabitResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(CreateHabitIntent.NameChanged("Drink water"))
            val events = mutableListOf<CreateHabitUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(CreateHabitIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(events.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `CancelClicked emits Cancelled`() =
        runTest {
            val viewModel = viewModel()
            val events = mutableListOf<CreateHabitUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(CreateHabitIntent.CancelClicked)
            runCurrent()

            assertEquals(listOf(CreateHabitUiEvent.Cancelled), events)
            collectJob.cancel()
        }
}
