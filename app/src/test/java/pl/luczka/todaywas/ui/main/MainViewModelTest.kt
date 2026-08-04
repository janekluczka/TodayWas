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
        id = date.hashCode().toLong(),
        date = date,
        text = text,
        createdAt = Instant.now(),
    )

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
    fun `uiState reflects focus and entries from both sources`() =
        runTest {
            val today = entry(LocalDate.now())
            val viewModel = viewModel(
                focus = Focus.JOURNAL,
                entries = listOf(today),
            )

            val state = viewModel.uiState.value

            assertEquals(FocusUiState.JOURNAL, state.focus)
            assertEquals(listOf(today.toUiState()), state.journalEntries)
        }

    @Test
    fun `habits reflects not-logged status when no check-in exists for today`() =
        runTest {
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = 1L, name = "Drink water")),
            )

            val state = viewModel.uiState.value.habits
                .single()

            assertEquals("Drink water", state.name)
            assertEquals(HabitCheckInStatusUiState.NotLogged, state.todayStatus)
        }

    @Test
    fun `habits reflects LoggedBinary status when a binary check-in exists for today`() =
        runTest {
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = 1L, type = HabitType.BINARY)),
                checkIns = listOf(checkIn(habitId = 1L, date = LocalDate.now(), value = 1)),
            )

            val state = viewModel.uiState.value.habits
                .single()

            assertEquals(HabitCheckInStatusUiState.LoggedBinary(done = true), state.todayStatus)
        }

    @Test
    fun `habits reflects LoggedScale status when a scale check-in exists for today`() =
        runTest {
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = 1L, type = HabitType.SCALE, scaleMin = 1, scaleMax = 5)),
                checkIns = listOf(checkIn(habitId = 1L, date = LocalDate.now(), value = 3)),
            )

            val state = viewModel.uiState.value.habits
                .single()

            assertEquals(HabitCheckInStatusUiState.LoggedScale(value = 3), state.todayStatus)
        }

    @Test
    fun `habits ignores a check-in logged for a different day`() =
        runTest {
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = 1L)),
                checkIns = listOf(checkIn(habitId = 1L, date = LocalDate.now().minusDays(1), value = 1)),
            )

            assertEquals(
                HabitCheckInStatusUiState.NotLogged,
                viewModel.uiState.value.habits
                    .single()
                    .todayStatus,
            )
        }

    @Test
    fun `fabActions includes ADD_JOURNAL_ENTRY when focus is JOURNAL and a slot is addable`() =
        runTest {
            val viewModel = viewModel(
                focus = Focus.JOURNAL,
                entries = emptyList(),
            )

            assertEquals(
                listOf(FabActionUiState.ADD_JOURNAL_ENTRY),
                viewModel.uiState.value.fabActions,
            )
        }

    @Test
    fun `fabActions excludes ADD_JOURNAL_ENTRY when no slots are addable`() =
        runTest {
            val entries = listOf(entry(LocalDate.now()), entry(LocalDate.now().minusDays(1)))
            val viewModel = viewModel(
                focus = Focus.JOURNAL,
                entries = entries,
            )

            assertTrue(FabActionUiState.ADD_JOURNAL_ENTRY !in viewModel.uiState.value.fabActions)
        }

    @Test
    fun `fabActions includes CREATE_HABIT but not LOG_HABIT_CHECK_INS when focus is HABIT and no habits exist`() =
        runTest {
            val viewModel = viewModel(focus = Focus.HABIT)

            assertEquals(listOf(FabActionUiState.CREATE_HABIT), viewModel.uiState.value.fabActions)
        }

    @Test
    fun `fabActions includes both CREATE_HABIT and LOG_HABIT_CHECK_INS when focus is HABIT and a habit exists`() =
        runTest {
            val viewModel = viewModel(
                focus = Focus.HABIT,
                habits = listOf(habit(id = 1L)),
            )

            assertEquals(
                listOf(FabActionUiState.CREATE_HABIT, FabActionUiState.LOG_HABIT_CHECK_INS),
                viewModel.uiState.value.fabActions,
            )
        }

    @Test
    fun `fabActions includes journal and habit actions together when focus is BOTH`() =
        runTest {
            val viewModel = viewModel(
                focus = Focus.BOTH,
                habits = listOf(habit(id = 1L)),
            )

            assertEquals(
                listOf(FabActionUiState.ADD_JOURNAL_ENTRY, FabActionUiState.CREATE_HABIT, FabActionUiState.LOG_HABIT_CHECK_INS),
                viewModel.uiState.value.fabActions,
            )
        }

    @Test
    fun `FabActionClicked with ADD_JOURNAL_ENTRY emits NavigateToAddEntry and collapses the fab`() =
        runTest {
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now())))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.FabToggled)
            viewModel.onIntent(MainIntent.FabActionClicked(FabActionUiState.ADD_JOURNAL_ENTRY))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToAddEntry), events)
            assertEquals(false, viewModel.uiState.value.fabExpanded)
            collectJob.cancel()
        }

    @Test
    fun `FabActionClicked with CREATE_HABIT emits NavigateToCreateHabit`() =
        runTest {
            val viewModel = viewModel(focus = Focus.HABIT)
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.FabActionClicked(FabActionUiState.CREATE_HABIT))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToCreateHabit), events)
            collectJob.cancel()
        }

    @Test
    fun `FabActionClicked with LOG_HABIT_CHECK_INS emits NavigateToLogHabitCheckIns`() =
        runTest {
            val viewModel = viewModel(focus = Focus.HABIT, habits = listOf(habit(id = 1L)))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.FabActionClicked(FabActionUiState.LOG_HABIT_CHECK_INS))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToLogHabitCheckIns), events)
            collectJob.cancel()
        }

    @Test
    fun `FabToggled flips fabExpanded`() =
        runTest {
            val viewModel = viewModel(entries = emptyList())

            viewModel.onIntent(MainIntent.FabToggled)

            assertTrue(viewModel.uiState.value.fabExpanded)
        }

    @Test
    fun `JournalEntryClicked emits NavigateToJournalDetail with the clicked entry`() =
        runTest {
            val today = entry(LocalDate.now())
            val viewModel = viewModel(entries = listOf(today))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.JournalEntryClicked(today.toUiState()))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToJournalDetail(today.toUiState())), events)
            collectJob.cancel()
        }

    @Test
    fun `HabitClicked emits NavigateToHabitDetail with the clicked habit's id`() =
        runTest {
            val viewModel = viewModel(focus = Focus.HABIT, habits = listOf(habit(id = 1L)))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.HabitClicked(habit(id = 1L).toUiState(todayCheckIn = null)))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToHabitDetail(1L)), events)
            collectJob.cancel()
        }

    @Test
    fun `journalContributionGrid and window state reflect loaded entries`() =
        runTest {
            val today = entry(LocalDate.now())
            val viewModel = viewModel(entries = listOf(today))

            val state = viewModel.uiState.value

            assertEquals(ContributionWindowUiState.RollingTwelveMonths, state.journalSelectedWindow)
            assertTrue(state.journalAvailableWindows.contains(ContributionWindowUiState.RollingTwelveMonths))
            assertTrue(state.journalAvailableWindows.contains(ContributionWindowUiState.CalendarYear(LocalDate.now().year)))
            assertTrue(levelFor(state.journalContributionGrid.cells, LocalDate.now()) != null)
        }

    @Test
    fun `JournalWindowSelected updates journalSelectedWindow and recomputes the grid without changing an already-visible day's level`() =
        runTest {
            val today = entry(LocalDate.now())
            val older = entry(LocalDate.now().minusDays(3))
            val viewModel = viewModel(entries = listOf(today, older))

            val levelBefore = levelFor(viewModel.uiState.value.journalContributionGrid.cells, LocalDate.now())

            viewModel.onIntent(
                MainIntent.JournalWindowSelected(ContributionWindowUiState.CalendarYear(LocalDate.now().year)),
            )
            runCurrent()

            assertEquals(
                ContributionWindowUiState.CalendarYear(LocalDate.now().year),
                viewModel.uiState.value.journalSelectedWindow,
            )
            assertEquals(levelBefore, levelFor(viewModel.uiState.value.journalContributionGrid.cells, LocalDate.now()))
        }

    @Test
    fun `journalContributionGrid instance is reused across an unrelated state change`() =
        runTest {
            val today = entry(LocalDate.now())
            val viewModel = viewModel(entries = listOf(today))

            val gridBefore = viewModel.uiState.value.journalContributionGrid

            viewModel.onIntent(MainIntent.FabToggled)
            runCurrent()

            assertTrue(gridBefore === viewModel.uiState.value.journalContributionGrid)
        }
}
