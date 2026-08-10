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
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.UpdateHabitCheckInUseCase
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
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

    private fun levelFor(
        cells: List<DsContributionCellUiState>,
        date: LocalDate,
    ): DsContributionLevel? = cells
        .filterIsInstance<DsContributionCellUiState.Level>()
        .find { it.date == date }
        ?.level

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should include every logged date plus today and yesterday even if unlogged in rows`() =
        runTest {
            // Arrange
            val oldDate = today.minusDays(10)
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = oldDate, value = 1, createdAt = now.minus(Duration.ofDays(10))),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }

            // Act
            runCurrent()
            val dates = viewModel.uiState.value.rows
                .map { it.date }

            // Assert
            assertTrue(dates.contains(oldDate))
            assertTrue(dates.contains(today))
            assertTrue(dates.contains(yesterday))
            assertEquals(3, dates.size)
            collectJob.cancel()
        }

    @Test
    fun `should derive eligibleForEdit true and alreadyLogged false when a day is not yet logged`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }

            // Act
            runCurrent()
            val row = viewModel.uiState.value.rows
                .find { it.date == today }

            // Assert
            assertEquals("Drink water", viewModel.uiState.value.habitName)
            assertTrue(row?.eligibleForEdit == true)
            assertFalse(row?.alreadyLogged == true)
            collectJob.cancel()
        }

    @Test
    fun `should derive eligibleForEdit true and alreadyLogged true when a logged day is within the window`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = today, value = 1, createdAt = now.minus(Duration.ofHours(1))),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }

            // Act
            runCurrent()
            val row = viewModel.uiState.value.rows
                .find { it.date == today }

            // Assert
            assertEquals(1, row?.value)
            assertTrue(row?.alreadyLogged == true)
            assertTrue(row?.eligibleForEdit == true)
            collectJob.cancel()
        }

    @Test
    fun `should derive eligibleForEdit false and alreadyLogged true when a logged day is past the window`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = yesterday, value = 0, createdAt = now.minus(Duration.ofHours(25))),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }

            // Act
            runCurrent()
            val row = viewModel.uiState.value.rows
                .find { it.date == yesterday }

            // Assert
            assertTrue(row?.alreadyLogged == true)
            assertFalse(row?.eligibleForEdit == true)
            collectJob.cancel()
        }

    @Test
    fun `should set isEditSheetOpen true when EditClicked is dispatched`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()

            // Act
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            runCurrent()

            // Assert
            assertTrue(viewModel.uiState.value.isEditSheetOpen)
            collectJob.cancel()
        }

    @Test
    fun `should exit edit mode and discard pending values when CancelEditClicked is dispatched`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 1))

            // Act
            viewModel.onIntent(HabitDetailIntent.CancelEditClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isEditSheetOpen)
            assertEquals(
                null,
                viewModel.uiState.value.rows
                    .find { it.date == today }
                    ?.value,
            )
            collectJob.cancel()
        }

    @Test
    fun `should exit edit mode without calling either use case when Save has nothing pending`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            viewModel.onIntent(HabitDetailIntent.EditClicked)

            // Act
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isEditSheetOpen)
            assertEquals(null, repository.lastLoggedDate)
            assertEquals(0, repository.updateCheckInCallCount)
            collectJob.cancel()
        }

    @Test
    fun `should call only the insert path and exit edit mode when Save touches only a not-yet-logged row`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 1))

            // Act
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isEditSheetOpen)
            assertEquals(today, repository.lastLoggedDate)
            assertEquals(mapOf(1L to 1), repository.lastLoggedValues)
            assertEquals(0, repository.updateCheckInCallCount)
            collectJob.cancel()
        }

    @Test
    fun `should call only the update path when Save touches only an in-window logged row`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = today, value = 1, createdAt = now.minus(Duration.ofHours(1))),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 0))

            // Act
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.updateCheckInCallCount)
            assertEquals(today, repository.lastUpdatedDate)
            assertEquals(0, repository.lastUpdatedValue)
            collectJob.cancel()
        }

    @Test
    fun `should call both paths and clear pending only if both succeed when Save touches a new and an existing row`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = today, value = 1, createdAt = now.minus(Duration.ofHours(1))),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 0))
            viewModel.onIntent(HabitDetailIntent.ValueChanged(yesterday, 1))

            // Act
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.updateCheckInCallCount)
            assertEquals(yesterday, repository.lastLoggedDate)
            assertEquals(mapOf(1L to 1), repository.lastLoggedValues)
            collectJob.cancel()
        }

    @Test
    fun `should set saveErrorIsWindowExpired when Save fails on a window-expired row`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = today, value = 1, createdAt = now.minus(Duration.ofHours(25))),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            // Bypasses the UI's enabled gate on purpose, mirroring a real mid-session expiry.
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 0))

            // Act
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(viewModel.uiState.value.saveErrorIsWindowExpired)
            assertEquals(0, repository.updateCheckInCallCount)
            collectJob.cancel()
        }

    @Test
    fun `should reflect loaded check-ins in contributionGrid and window state`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = today, value = 1, createdAt = now),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }

            // Act
            runCurrent()
            val state = viewModel.uiState.value

            // Assert
            assertEquals(ContributionWindowUiState.RollingTwelveMonths, state.selectedWindow)
            assertTrue(state.availableWindows.contains(ContributionWindowUiState.RollingTwelveMonths))
            assertTrue(state.availableWindows.contains(ContributionWindowUiState.CalendarYear(today.year)))
            assertEquals(DsContributionLevel.LEVEL_5, levelFor(state.contributionGrid.cells, today))
            collectJob.cancel()
        }

    @Test
    fun `should update selectedWindow without changing an already-visible day's level when WindowSelected is dispatched`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = today, value = 1, createdAt = now),
                    HabitCheckIn(id = 2L, habitId = 1L, date = today.minusDays(3), value = 0, createdAt = now.minus(Duration.ofDays(3))),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            val levelBefore = levelFor(viewModel.uiState.value.contributionGrid.cells, today)

            // Act
            viewModel.onIntent(HabitDetailIntent.WindowSelected(ContributionWindowUiState.CalendarYear(today.year)))
            runCurrent()

            // Assert
            assertEquals(ContributionWindowUiState.CalendarYear(today.year), viewModel.uiState.value.selectedWindow)
            assertEquals(levelBefore, levelFor(viewModel.uiState.value.contributionGrid.cells, today))
            collectJob.cancel()
        }

    @Test
    fun `should reuse the contributionGrid instance across unrelated state changes during editing`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialHabits = listOf(habit),
                initialCheckIns = listOf(
                    HabitCheckIn(id = 1L, habitId = 1L, date = today, value = 1, createdAt = now),
                ),
            )
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            val gridBefore = viewModel.uiState.value.contributionGrid

            // Act
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            viewModel.onIntent(HabitDetailIntent.ValueChanged(yesterday, 1))
            runCurrent()

            // Assert
            assertTrue(gridBefore === viewModel.uiState.value.contributionGrid)
            collectJob.cancel()
        }

    @Test
    fun `should clear saveErrorIsWindowExpired when Save fails for a generic reason`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(initialHabits = listOf(habit))
            repository.addCheckInsResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            val collectJob = launch { viewModel.uiState.collect {} }
            runCurrent()
            viewModel.onIntent(HabitDetailIntent.EditClicked)
            viewModel.onIntent(HabitDetailIntent.ValueChanged(today, 1))

            // Act
            viewModel.onIntent(HabitDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertTrue(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.saveErrorIsWindowExpired)
            collectJob.cancel()
        }
}
