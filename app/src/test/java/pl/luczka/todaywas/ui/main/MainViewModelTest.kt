package pl.luczka.todaywas.ui.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.toUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private class FakeOnboardingRepository(
        initialState: OnboardingState,
    ) : OnboardingRepository {

        private val stateFlow = MutableStateFlow(initialState)

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun saveFocus(focus: Focus): Result<Unit> = Result.success(Unit)
    }

    private fun entry(
        date: LocalDate,
        text: String = "entry",
    ) = JournalEntry(
        id = date.hashCode().toString(),
        date = date,
        text = text,
        createdAt = Instant.now(),
    )

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
    )

    private fun viewModel(
        focus: Focus? = Focus.JOURNAL,
        entries: List<JournalEntry> = emptyList(),
        habits: List<Habit> = emptyList(),
        checkIns: List<HabitCheckIn> = emptyList(),
        clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC),
    ): MainViewModel {
        val journalRepository = FakeJournalRepository(entries)
        val habitRepository = FakeHabitRepository(habits, checkIns)
        return MainViewModel(
            observeOnboardingState = ObserveOnboardingStateUseCase(
                FakeOnboardingRepository(
                    OnboardingState(
                        completed = true,
                        focus = focus,
                    ),
                ),
            ),
            observeJournalEntries = ObserveJournalEntriesUseCase(journalRepository),
            observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(journalRepository),
            observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(habitRepository),
            clock = clock,
        )
    }

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
    fun `should reflect focus and entries from both sources in uiState`() =
        runTest {
            // Arrange
            val today = entry(LocalDate.now())

            // Act
            val viewModel = viewModel(
                focus = Focus.JOURNAL,
                entries = listOf(today),
            )
            val state = viewModel.uiState.value

            // Assert
            assertEquals(FocusUiState.JOURNAL, state.focus)
            assertEquals(listOf(today.toUiState()), state.journalEntries)
        }

    @Test
    fun `should reflect not-logged status in habits when no check-in exists for today`() =
        runTest {
            // Arrange
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = "1", name = "Drink water")),
            )

            // Act
            val state = viewModel.uiState.value.habits
                .single()

            // Assert
            assertEquals("Drink water", state.name)
            assertEquals(HabitCheckInStatusUiState.NotLogged, state.todayStatus)
        }

    @Test
    fun `should reflect LoggedBinary status in habits when a binary check-in exists for today`() =
        runTest {
            // Arrange
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = "1", type = HabitType.BINARY)),
                checkIns = listOf(checkIn(habitId = "1", date = LocalDate.now(), value = 1)),
            )

            // Act
            val state = viewModel.uiState.value.habits
                .single()

            // Assert
            assertEquals(HabitCheckInStatusUiState.LoggedBinary(done = true), state.todayStatus)
        }

    @Test
    fun `should reflect LoggedScale status in habits when a scale check-in exists for today`() =
        runTest {
            // Arrange
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = "1", type = HabitType.SCALE, scaleMin = 1, scaleMax = 5)),
                checkIns = listOf(checkIn(habitId = "1", date = LocalDate.now(), value = 3)),
            )

            // Act
            val state = viewModel.uiState.value.habits
                .single()

            // Assert
            assertEquals(HabitCheckInStatusUiState.LoggedScale(value = 3), state.todayStatus)
        }

    @Test
    fun `should ignore a check-in logged for a different day in habits`() =
        runTest {
            // Arrange
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = "1")),
                checkIns = listOf(checkIn(habitId = "1", date = LocalDate.now().minusDays(1), value = 1)),
            )

            // Act
            val status = viewModel.uiState.value.habits
                .single()
                .todayStatus

            // Assert
            assertEquals(HabitCheckInStatusUiState.NotLogged, status)
        }

    @Test
    fun `should include ADD_JOURNAL_ENTRY in fabActions when focus is JOURNAL and a slot is addable`() =
        runTest {
            // Arrange
            val viewModel = viewModel(
                focus = Focus.JOURNAL,
                entries = emptyList(),
            )

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertEquals(listOf(FabActionUiState.ADD_JOURNAL_ENTRY), fabActions)
        }

    @Test
    fun `should exclude ADD_JOURNAL_ENTRY from fabActions when no slots are addable`() =
        runTest {
            // Arrange
            val entries = listOf(entry(LocalDate.now()), entry(LocalDate.now().minusDays(1)))
            val viewModel = viewModel(
                focus = Focus.JOURNAL,
                entries = entries,
            )

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertTrue(FabActionUiState.ADD_JOURNAL_ENTRY !in fabActions)
        }

    @Test
    fun `should include CREATE_HABIT but not LOG_HABIT_CHECK_INS in fabActions when focus is HABIT and no habits exist`() =
        runTest {
            // Arrange
            val viewModel = viewModel(focus = Focus.HABIT)

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertEquals(listOf(FabActionUiState.CREATE_HABIT), fabActions)
        }

    @Test
    fun `should include both CREATE_HABIT and LOG_HABIT_CHECK_INS in fabActions when focus is HABIT and a habit exists`() =
        runTest {
            // Arrange
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = "1")),
            )

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertEquals(
                listOf(FabActionUiState.CREATE_HABIT, FabActionUiState.LOG_HABIT_CHECK_INS),
                fabActions,
            )
        }

    @Test
    fun `should include journal and habit actions together in fabActions when focus is BOTH`() =
        runTest {
            // Arrange
            val viewModel = viewModel(
                focus = Focus.BOTH,
                habits = listOf(habit(id = "1")),
            )

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertEquals(
                listOf(FabActionUiState.ADD_JOURNAL_ENTRY, FabActionUiState.CREATE_HABIT, FabActionUiState.LOG_HABIT_CHECK_INS),
                fabActions,
            )
        }

    @Test
    fun `should emit NavigateToAddEntry and collapse the fab when FabActionClicked with ADD_JOURNAL_ENTRY`() =
        runTest {
            // Arrange
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now())))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(MainIntent.FabToggled)
            viewModel.onIntent(MainIntent.FabActionClicked(FabActionUiState.ADD_JOURNAL_ENTRY))
            runCurrent()

            // Assert
            assertEquals(listOf(MainUiEvent.NavigateToAddEntry), events)
            assertEquals(false, viewModel.uiState.value.fabExpanded)
            collectJob.cancel()
        }

    @Test
    fun `should emit NavigateToCreateHabit when FabActionClicked with CREATE_HABIT`() =
        runTest {
            // Arrange
            val viewModel = viewModel(focus = Focus.HABIT)
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(MainIntent.FabActionClicked(FabActionUiState.CREATE_HABIT))
            runCurrent()

            // Assert
            assertEquals(listOf(MainUiEvent.NavigateToCreateHabit), events)
            collectJob.cancel()
        }

    @Test
    fun `should emit NavigateToLogHabitCheckIns when FabActionClicked with LOG_HABIT_CHECK_INS`() =
        runTest {
            // Arrange
            val viewModel = viewModel(focus = Focus.HABIT, habits = listOf(habit(id = "1")))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(MainIntent.FabActionClicked(FabActionUiState.LOG_HABIT_CHECK_INS))
            runCurrent()

            // Assert
            assertEquals(listOf(MainUiEvent.NavigateToLogHabitCheckIns), events)
            collectJob.cancel()
        }

    @Test
    fun `should flip fabExpanded when FabToggled is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel(entries = emptyList())

            // Act
            viewModel.onIntent(MainIntent.FabToggled)

            // Assert
            assertTrue(viewModel.uiState.value.fabExpanded)
        }

    @Test
    fun `should emit NavigateToJournalDetail with the clicked entry when JournalEntryClicked is dispatched`() =
        runTest {
            // Arrange
            val today = entry(LocalDate.now())
            val viewModel = viewModel(entries = listOf(today))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(MainIntent.JournalEntryClicked(today.toUiState()))
            runCurrent()

            // Assert
            assertEquals(listOf(MainUiEvent.NavigateToJournalDetail(today.toUiState())), events)
            collectJob.cancel()
        }

    @Test
    fun `should emit NavigateToHabitDetail with the clicked habit's id when HabitClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel(focus = Focus.HABIT, habits = listOf(habit(id = "1")))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(MainIntent.HabitClicked(habit(id = "1").toUiState(todayCheckIn = null)))
            runCurrent()

            // Assert
            assertEquals(listOf(MainUiEvent.NavigateToHabitDetail("1")), events)
            collectJob.cancel()
        }

    @Test
    fun `should reflect loaded entries in journalContributionGrid and window state`() =
        runTest {
            // Arrange
            val today = entry(LocalDate.now())

            // Act
            val viewModel = viewModel(entries = listOf(today))
            val state = viewModel.uiState.value

            // Assert
            assertEquals(ContributionWindowUiState.RollingTwelveMonths, state.journalSelectedWindow)
            assertTrue(state.journalAvailableWindows.contains(ContributionWindowUiState.RollingTwelveMonths))
            assertTrue(state.journalAvailableWindows.contains(ContributionWindowUiState.CalendarYear(LocalDate.now().year)))
            assertTrue(levelFor(state.journalContributionGrid.cells, LocalDate.now()) != null)
        }

    @Test
    fun `should update journalSelectedWindow without changing an already-visible day's level when JournalWindowSelected is dispatched`() =
        runTest {
            // Arrange
            val today = entry(LocalDate.now())
            val older = entry(LocalDate.now().minusDays(3))
            val viewModel = viewModel(entries = listOf(today, older))
            val levelBefore = levelFor(viewModel.uiState.value.journalContributionGrid.cells, LocalDate.now())

            // Act
            viewModel.onIntent(
                MainIntent.JournalWindowSelected(ContributionWindowUiState.CalendarYear(LocalDate.now().year)),
            )
            runCurrent()

            // Assert
            assertEquals(
                ContributionWindowUiState.CalendarYear(LocalDate.now().year),
                viewModel.uiState.value.journalSelectedWindow,
            )
            assertEquals(levelBefore, levelFor(viewModel.uiState.value.journalContributionGrid.cells, LocalDate.now()))
        }

    @Test
    fun `should reuse the journalContributionGrid instance across an unrelated state change`() =
        runTest {
            // Arrange
            val today = entry(LocalDate.now())
            val viewModel = viewModel(entries = listOf(today))
            val gridBefore = viewModel.uiState.value.journalContributionGrid

            // Act
            viewModel.onIntent(MainIntent.FabToggled)
            runCurrent()

            // Assert
            assertTrue(gridBefore === viewModel.uiState.value.journalContributionGrid)
        }
}
