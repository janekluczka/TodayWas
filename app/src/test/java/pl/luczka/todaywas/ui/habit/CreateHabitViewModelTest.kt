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
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.usecase.CreateHabitUseCase

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
    fun `initial state is blank, binary, and defaults to the minimum scale step count`() =
        runTest {
            val viewModel = viewModel()

            val state = viewModel.uiState.value

            assertEquals("", state.name)
            assertEquals("", state.description)
            assertEquals(HabitScaleStepsRange.first, state.scaleSteps)
            assertTrue(state.isBinary)
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
    fun `isBinary is false once steps grow past the minimum`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(3))

            assertFalse(viewModel.uiState.value.isBinary)
        }

    @Test
    fun `ScaleStepsChanged updates scaleSteps within bounds`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(5))

            assertEquals(5, viewModel.uiState.value.scaleSteps)
        }

    @Test
    fun `ScaleStepsChanged coerces values outside the allowed range`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(1))
            assertEquals(HabitScaleStepsRange.first, viewModel.uiState.value.scaleSteps)

            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(9))
            assertEquals(HabitScaleStepsRange.last, viewModel.uiState.value.scaleSteps)
        }

    @Test
    fun `SaveClicked with more than the minimum steps saves a SCALE habit with 1 to scaleSteps as the range`() =
        runTest {
            val repository = FakeHabitRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(CreateHabitIntent.NameChanged("Mood"))
            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(5))
            val events = mutableListOf<CreateHabitUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(CreateHabitIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.createHabitCallCount)
            assertEquals(HabitType.SCALE, repository.lastCreatedType)
            assertEquals(1, repository.lastCreatedScaleMin)
            assertEquals(5, repository.lastCreatedScaleMax)
            assertEquals(listOf(CreateHabitUiEvent.Saved), events)
            collectJob.cancel()
        }

    @Test
    fun `SaveClicked at the minimum step count saves a BINARY habit with null scaleMin and scaleMax`() =
        runTest {
            val repository = FakeHabitRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(CreateHabitIntent.NameChanged("Drink water"))

            viewModel.onIntent(CreateHabitIntent.SaveClicked)
            runCurrent()

            assertEquals(HabitType.BINARY, repository.lastCreatedType)
            assertEquals(null, repository.lastCreatedScaleMin)
            assertEquals(null, repository.lastCreatedScaleMax)
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
