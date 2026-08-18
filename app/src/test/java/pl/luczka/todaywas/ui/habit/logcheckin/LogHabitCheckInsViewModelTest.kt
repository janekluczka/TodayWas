package pl.luczka.todaywas.ui.habit.logcheckin

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
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LogHabitCheckInsViewModelTest {

    private fun habit(
        id: String,
        name: String = "habit-$id",
        type: HabitType = HabitType.BINARY,
        scaleMin: Int? = null,
        scaleMax: Int? = null,
    ) = Habit(
        id = id,
        name = name,
        description = null,
        type = type,
        scaleMin = scaleMin,
        scaleMax = scaleMax,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun checkIn(
        habitId: String,
        date: LocalDate,
        value: Int,
    ) = HabitCheckIn(
        id = habitId,
        habitId = habitId,
        date = date,
        value = value,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun viewModel(repository: FakeHabitRepository) = LogHabitCheckInsViewModel(
        observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(repository),
        logHabitCheckIns = LogHabitCheckInsUseCase(repository),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should contain exactly yesterday and today in selectableDates`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeHabitRepository())

            // Act
            val dates = viewModel.uiState.value.selectableDates

            // Assert
            assertEquals(2, dates.size)
            assertEquals(LocalDate.now(), dates.last())
            assertEquals(LocalDate.now().minusDays(1), dates.first())
            assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `should render as an editable row with a 0 to 1 range when a binary habit is unlogged`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = "1", name = "Drink water")))
            val viewModel = viewModel(repository)

            // Act
            val row = viewModel.uiState.value.rows
                .single()

            // Assert
            assertEquals(
                HabitCheckInRowUiState.Editable(
                    habitId = "1",
                    name = "Drink water",
                    value = null,
                    range = 0..1,
                    type = HabitTypeUiState.BINARY,
                ),
                row,
            )
        }

    @Test
    fun `should render as an editable row with its range when a scale habit is unlogged`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit(id = "1", type = HabitType.SCALE, scaleMin = 1, scaleMax = 5)),
            )
            val viewModel = viewModel(repository)

            // Act
            val row = viewModel.uiState.value.rows
                .single() as HabitCheckInRowUiState.Editable

            // Assert
            assertEquals(1..5, row.range)
            assertEquals(null, row.value)
        }

    @Test
    fun `should render as AlreadyLogged when a habit is already logged for the selected date`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit(id = "1", type = HabitType.BINARY)),
                initialCheckIns = listOf(checkIn(habitId = "1", date = LocalDate.now(), value = 1)),
            )
            val viewModel = viewModel(repository)

            // Act
            val row = viewModel.uiState.value.rows
                .single()

            // Assert
            assertEquals(
                HabitCheckInRowUiState.AlreadyLogged(
                    habitId = "1",
                    name = "habit-1",
                    range = 0..1,
                    type = HabitTypeUiState.BINARY,
                    value = 1,
                ),
                row,
            )
        }

    @Test
    fun `should render a mix of editable and already-logged rows when habits are mixed`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit(id = "1"), habit(id = "2")),
                initialCheckIns = listOf(checkIn(habitId = "1", date = LocalDate.now(), value = 1)),
            )
            val viewModel = viewModel(repository)

            // Act
            val rows = viewModel.uiState.value.rows

            // Assert
            assertTrue(rows[0] is HabitCheckInRowUiState.AlreadyLogged)
            assertTrue(rows[1] is HabitCheckInRowUiState.Editable)
        }

    @Test
    fun `should update the pending value for that row when ValueChanged is dispatched`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = "1")))
            val viewModel = viewModel(repository)

            // Act
            viewModel.onIntent(LogHabitCheckInsIntent.ValueChanged(habitId = "1", value = 1))

            // Assert
            val row = viewModel.uiState.value.rows
                .single() as HabitCheckInRowUiState.Editable
            assertEquals(1, row.value)
        }

    @Test
    fun `should clear a previously entered pending value when ValueChanged is dispatched with null`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = "1")))
            val viewModel = viewModel(repository)
            viewModel.onIntent(LogHabitCheckInsIntent.ValueChanged(habitId = "1", value = 1))

            // Act
            viewModel.onIntent(LogHabitCheckInsIntent.ValueChanged(habitId = "1", value = null))

            // Assert
            val row = viewModel.uiState.value.rows
                .single() as HabitCheckInRowUiState.Editable
            assertEquals(null, row.value)
        }

    @Test
    fun `should switch selectedDate and clear pending input when DateSelected is dispatched`() =
        runTest {
            // Arrange
            val yesterday = LocalDate.now().minusDays(1)
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = "1")))
            val viewModel = viewModel(repository)
            viewModel.onIntent(LogHabitCheckInsIntent.ValueChanged(habitId = "1", value = 1))

            // Act
            viewModel.onIntent(LogHabitCheckInsIntent.DateSelected(yesterday))

            // Assert
            assertEquals(yesterday, viewModel.uiState.value.selectedDate)
            val row = viewModel.uiState.value.rows
                .single() as HabitCheckInRowUiState.Editable
            assertEquals(null, row.value)
        }

    @Test
    fun `should save entered values for the selected date and emit Saved when SaveClicked succeeds`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = "1")))
            val viewModel = viewModel(repository)
            viewModel.onIntent(LogHabitCheckInsIntent.ValueChanged(habitId = "1", value = 1))
            val events = mutableListOf<LogHabitCheckInsUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(LogHabitCheckInsIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(LocalDate.now(), repository.lastLoggedDate)
            assertEquals(mapOf("1" to 1), repository.lastLoggedValues)
            assertEquals(listOf(LogHabitCheckInsUiEvent.Saved), events)
            collectJob.cancel()
        }

    @Test
    fun `should set saveError and not emit Saved when SaveClicked fails`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = "1")))
            repository.addCheckInsResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(LogHabitCheckInsIntent.ValueChanged(habitId = "1", value = 1))
            val events = mutableListOf<LogHabitCheckInsUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(LogHabitCheckInsIntent.SaveClicked)
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
            val viewModel = viewModel(FakeHabitRepository())
            val events = mutableListOf<LogHabitCheckInsUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(LogHabitCheckInsIntent.CancelClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(LogHabitCheckInsUiEvent.Cancelled), events)
            collectJob.cancel()
        }
}
