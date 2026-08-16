package pl.luczka.todaywas.ui.habit.create

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
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
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
    fun `should be blank, binary, and default to the minimum scale step count on initial state`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            val state = viewModel.uiState.value

            // Assert
            assertEquals("", state.name)
            assertEquals("", state.description)
            assertEquals(HabitScaleStepsRange.first, state.scaleSteps)
            assertTrue(state.isBinary)
            assertFalse(state.isSaving)
            assertFalse(state.saveError)
        }

    @Test
    fun `should update name when NameChanged is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(CreateHabitIntent.NameChanged("Drink water"))

            // Assert
            assertEquals("Drink water", viewModel.uiState.value.name)
        }

    @Test
    fun `should become false when steps grow past the minimum`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(3))

            // Assert
            assertFalse(viewModel.uiState.value.isBinary)
        }

    @Test
    fun `should update scaleSteps within bounds when ScaleStepsChanged is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(5))

            // Assert
            assertEquals(5, viewModel.uiState.value.scaleSteps)
        }

    @Test
    fun `should coerce values outside the allowed range when ScaleStepsChanged is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(1))
            val afterBelowMinimum = viewModel.uiState.value.scaleSteps
            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(9))
            val afterAboveMaximum = viewModel.uiState.value.scaleSteps

            // Assert
            assertEquals(HabitScaleStepsRange.first, afterBelowMinimum)
            assertEquals(HabitScaleStepsRange.last, afterAboveMaximum)
        }

    @Test
    fun `should save a SCALE habit with 1 to scaleSteps as the range when SaveClicked with more than the minimum steps`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(CreateHabitIntent.NameChanged("Mood"))
            viewModel.onIntent(CreateHabitIntent.ScaleStepsChanged(5))
            val events = mutableListOf<CreateHabitUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(CreateHabitIntent.SaveClicked)
            runCurrent()

            // Assert
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
    fun `should save a BINARY habit with null scaleMin and scaleMax when SaveClicked at the minimum step count`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(CreateHabitIntent.NameChanged("Drink water"))

            // Act
            viewModel.onIntent(CreateHabitIntent.SaveClicked)
            runCurrent()

            // Assert
            assertEquals(HabitType.BINARY, repository.lastCreatedType)
            assertEquals(null, repository.lastCreatedScaleMin)
            assertEquals(null, repository.lastCreatedScaleMax)
        }

    @Test
    fun `should set saveError and not emit Saved when SaveClicked fails`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            repository.createHabitResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(CreateHabitIntent.NameChanged("Drink water"))
            val events = mutableListOf<CreateHabitUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(CreateHabitIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(events.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `should emit Cancelled when CancelClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            val events = mutableListOf<CreateHabitUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(CreateHabitIntent.CancelClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(CreateHabitUiEvent.Cancelled), events)
            collectJob.cancel()
        }
}
