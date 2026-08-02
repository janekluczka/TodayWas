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
import pl.luczka.todaywas.domain.usecase.UpdateHabitCheckInUseCase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HabitDetailViewModelTest {

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val now = Instant.now()
    private val habit = Habit(
        id = 1L,
        name = "Drink water",
        description = null,
        type = HabitType.BINARY,
        scaleMin = null,
        scaleMax = null,
        createdAt = now,
    )

    private fun viewModel(
        repository: FakeHabitRepository,
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
        habitId: Long = 1L,
    ) = HabitDetailViewModel(
        habitId = habitId,
        observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(repository),
        logHabitCheckIns = LogHabitCheckInsUseCase(repository),
        updateHabitCheckIn = UpdateHabitCheckInUseCase(repository, clock),
        clock = clock,
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
    fun `a not-yet-logged day derives editable true and alreadyLogged false`() =
        runTest {
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()

            val row = viewModel.uiState.value.rows
                .find { it.date == today }

            assertEquals("Drink water", viewModel.uiState.value.habitName)
            assertTrue(row?.editable == true)
            assertFalse(row?.alreadyLogged == true)
            collectJob.cancel()
        }

    @Test
    fun `a logged day within the window derives editable true and alreadyLogged true`() =
        runTest {
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(
                        id = 1L,
                        habitId = 1L,
                        date = today,
                        value = 1,
                        createdAt = now.minus(Duration.ofHours(1)),
                    ),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()

            val row = viewModel.uiState.value.rows
                .find { it.date == today }

            assertEquals(1, row?.value)
            assertTrue(row?.alreadyLogged == true)
            assertTrue(row?.editable == true)
            collectJob.cancel()
        }

    @Test
    fun `a logged day past the window derives editable false and alreadyLogged true`() =
        runTest {
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(
                        id = 1L,
                        habitId = 1L,
                        date = yesterday,
                        value = 0,
                        createdAt = now.minus(Duration.ofHours(25)),
                    ),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()

            val row = viewModel.uiState.value.rows
                .find { it.date == yesterday }

            assertTrue(row?.alreadyLogged == true)
            assertFalse(row?.editable == true)
            collectJob.cancel()
        }

    @Test
    fun `Save touching only a not-yet-logged row calls only the insert path`() =
        runTest {
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()

            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 1))
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(today, repository.lastLoggedDate)
            assertEquals(mapOf(1L to 1), repository.lastLoggedValues)
            assertEquals(0, repository.updateCheckInCallCount)
            collectJob.cancel()
        }

    @Test
    fun `Save touching only an in-window logged row calls only the update path`() =
        runTest {
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(
                        id = 1L,
                        habitId = 1L,
                        date = today,
                        value = 1,
                        createdAt = now.minus(Duration.ofHours(1)),
                    ),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()

            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 0))
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.updateCheckInCallCount)
            assertEquals(today, repository.lastUpdatedDate)
            assertEquals(0, repository.lastUpdatedValue)
            collectJob.cancel()
        }

    @Test
    fun `Save touching both a new and an existing row calls both and clears pending only if both succeed`() =
        runTest {
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(
                        id = 1L,
                        habitId = 1L,
                        date = today,
                        value = 1,
                        createdAt = now.minus(Duration.ofHours(1)),
                    ),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()

            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 0))
            viewModel.onIntent(HabitDetailIntent.ValueChanged(yesterday, 1))
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.updateCheckInCallCount)
            assertEquals(yesterday, repository.lastLoggedDate)
            assertEquals(mapOf(1L to 1), repository.lastLoggedValues)
            collectJob.cancel()
        }
}
