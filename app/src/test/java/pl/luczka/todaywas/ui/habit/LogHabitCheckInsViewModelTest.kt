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
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LogHabitCheckInsViewModelTest {

    private fun habit(
        id: Long,
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
    )

    private fun checkIn(
        habitId: Long,
        date: LocalDate,
        value: Int,
    ) = HabitCheckIn(
        id = habitId,
        habitId = habitId,
        date = date,
        value = value,
        createdAt = Instant.EPOCH,
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
    fun `selectableDates contains exactly 7 dates ending today`() =
        runTest {
            val viewModel = viewModel(FakeHabitRepository())

            val dates = viewModel.uiState.value.selectableDates

            assertEquals(7, dates.size)
            assertEquals(LocalDate.now(), dates.last())
            assertEquals(LocalDate.now().minusDays(6), dates.first())
            assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `unlogged habit renders as an editable binary row`() =
        runTest {
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = 1L, name = "Drink water")))
            val viewModel = viewModel(repository)

            val row = viewModel.uiState.value.rows
                .single()

            assertEquals(HabitCheckInRowUiState.Editable.Binary(habitId = 1L, name = "Drink water", value = null), row)
        }

    @Test
    fun `unlogged scale habit renders as an editable scale row with its range`() =
        runTest {
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit(id = 1L, type = HabitType.SCALE, scaleMin = 1, scaleMax = 5)),
            )
            val viewModel = viewModel(repository)

            val row = viewModel.uiState.value.rows
                .single() as HabitCheckInRowUiState.Editable.Scale

            assertEquals(1..5, row.range)
            assertEquals(null, row.value)
        }

    @Test
    fun `habit already logged for the selected date renders as AlreadyLogged`() =
        runTest {
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit(id = 1L, type = HabitType.BINARY)),
                initialCheckIns = listOf(checkIn(habitId = 1L, date = LocalDate.now(), value = 1)),
            )
            val viewModel = viewModel(repository)

            val row = viewModel.uiState.value.rows
                .single()

            assertEquals(
                HabitCheckInRowUiState.AlreadyLogged(
                    habitId = 1L,
                    name = "habit-1",
                    status = HabitCheckInStatusUiState.LoggedBinary(done = true),
                ),
                row,
            )
        }

    @Test
    fun `mixed habits render as a mix of editable and already-logged rows`() =
        runTest {
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit(id = 1L), habit(id = 2L)),
                initialCheckIns = listOf(checkIn(habitId = 1L, date = LocalDate.now(), value = 1)),
            )
            val viewModel = viewModel(repository)

            val rows = viewModel.uiState.value.rows

            assertTrue(rows[0] is HabitCheckInRowUiState.AlreadyLogged)
            assertTrue(rows[1] is HabitCheckInRowUiState.Editable.Binary)
        }

    @Test
    fun `BinaryValueChanged updates the pending value for that row`() =
        runTest {
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = 1L)))
            val viewModel = viewModel(repository)

            viewModel.onIntent(LogHabitCheckInsIntent.BinaryValueChanged(habitId = 1L, value = true))

            val row = viewModel.uiState.value.rows
                .single() as HabitCheckInRowUiState.Editable.Binary
            assertEquals(true, row.value)
        }

    @Test
    fun `DateSelected switches selectedDate and clears pending input`() =
        runTest {
            val yesterday = LocalDate.now().minusDays(1)
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = 1L)))
            val viewModel = viewModel(repository)
            viewModel.onIntent(LogHabitCheckInsIntent.BinaryValueChanged(habitId = 1L, value = true))

            viewModel.onIntent(LogHabitCheckInsIntent.DateSelected(yesterday))

            assertEquals(yesterday, viewModel.uiState.value.selectedDate)
            val row = viewModel.uiState.value.rows
                .single() as HabitCheckInRowUiState.Editable.Binary
            assertEquals(null, row.value)
        }

    @Test
    fun `SaveClicked success saves entered values for the selected date and emits Saved`() =
        runTest {
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = 1L)))
            val viewModel = viewModel(repository)
            viewModel.onIntent(LogHabitCheckInsIntent.BinaryValueChanged(habitId = 1L, value = true))
            val events = mutableListOf<LogHabitCheckInsUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(LogHabitCheckInsIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(LocalDate.now(), repository.lastLoggedDate)
            assertEquals(mapOf(1L to 1), repository.lastLoggedValues)
            assertEquals(listOf(LogHabitCheckInsUiEvent.Saved), events)
            collectJob.cancel()
        }

    @Test
    fun `SaveClicked failure sets saveError and does not emit Saved`() =
        runTest {
            val repository = FakeHabitRepository(initialHabits = listOf(habit(id = 1L)))
            repository.addCheckInsResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(LogHabitCheckInsIntent.BinaryValueChanged(habitId = 1L, value = true))
            val events = mutableListOf<LogHabitCheckInsUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(LogHabitCheckInsIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(events.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `CancelClicked emits Cancelled`() =
        runTest {
            val viewModel = viewModel(FakeHabitRepository())
            val events = mutableListOf<LogHabitCheckInsUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(LogHabitCheckInsIntent.CancelClicked)
            runCurrent()

            assertEquals(listOf(LogHabitCheckInsUiEvent.Cancelled), events)
            collectJob.cancel()
        }
}
