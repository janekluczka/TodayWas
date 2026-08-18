package pl.luczka.todaywas.ui.main

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.repository.FakeOnboardingRepository
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private fun entry(
        date: LocalDate,
        text: String = "entry",
    ) = JournalEntry(
        id = date.hashCode().toString(),
        date = date,
        text = text,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
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

    private fun viewModel(
        entries: List<JournalEntry> = emptyList(),
        habits: List<Habit> = emptyList(),
        checkIns: List<HabitCheckIn> = emptyList(),
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC),
    ): MainViewModel {
        val journalRepository = FakeJournalRepository(entries)
        val habitRepository = FakeHabitRepository(habits, checkIns)
        val onboardingRepository = FakeOnboardingRepository()
        return MainViewModel(
            observeJournalEntries = ObserveJournalEntriesUseCase(journalRepository),
            observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(journalRepository),
            observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(habitRepository),
            observeAuthState = ObserveAuthStateUseCase(authRepository),
            syncLocalData = SyncLocalDataUseCase(journalRepository, habitRepository),
            signOut = SignOutUseCase(authRepository, journalRepository, habitRepository, onboardingRepository),
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
    fun `should reflect entries from both sources in uiState`() =
        runTest {
            // Arrange
            val today = entry(LocalDate.now())

            // Act
            val viewModel = viewModel(entries = listOf(today))
            val state = viewModel.uiState.value

            // Assert
            assertEquals(listOf(today.toUiState()), state.journalEntries)
        }

    @Test
    fun `should reflect not-logged status in habits when no check-in exists for today`() =
        runTest {
            // Arrange
            val viewModel = viewModel(habits = listOf(habit(id = "1", name = "Drink water")))

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
    fun `should include ADD_JOURNAL_ENTRY in fabActions when a journal slot is addable`() =
        runTest {
            // Arrange
            val viewModel = viewModel(entries = emptyList())

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertTrue(FabActionUiState.ADD_JOURNAL_ENTRY in fabActions)
        }

    @Test
    fun `should exclude ADD_JOURNAL_ENTRY from fabActions when no slots are addable`() =
        runTest {
            // Arrange
            val entries = listOf(entry(LocalDate.now()), entry(LocalDate.now().minusDays(1)))
            val viewModel = viewModel(entries = entries)

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertTrue(FabActionUiState.ADD_JOURNAL_ENTRY !in fabActions)
        }

    @Test
    fun `should include CREATE_HABIT but not LOG_HABIT_CHECK_INS in fabActions when no habits exist`() =
        runTest {
            // Arrange
            val entries = listOf(entry(LocalDate.now()))
            val viewModel = viewModel(entries = entries)

            // Act
            val fabActions = viewModel.uiState.value.fabActions

            // Assert
            assertTrue(FabActionUiState.CREATE_HABIT in fabActions)
            assertTrue(FabActionUiState.LOG_HABIT_CHECK_INS !in fabActions)
        }

    @Test
    fun `should include journal and habit actions together in fabActions when slots and habits exist`() =
        runTest {
            // Arrange
            val viewModel = viewModel(habits = listOf(habit(id = "1")))

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
            val viewModel = viewModel()
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
            val viewModel = viewModel(habits = listOf(habit(id = "1")))
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
            val viewModel = viewModel(habits = listOf(habit(id = "1")))
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

    @Test
    fun `should sync local data when the screen loads while already signed in`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))

            // Act
            MainViewModel(
                observeJournalEntries = ObserveJournalEntriesUseCase(journalRepository),
                observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(journalRepository),
                observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(habitRepository),
                observeAuthState = ObserveAuthStateUseCase(authRepository),
                syncLocalData = SyncLocalDataUseCase(journalRepository, habitRepository),
                signOut = SignOutUseCase(authRepository, journalRepository, habitRepository, onboardingRepository),
                clock = Clock.fixed(Instant.now(), ZoneOffset.UTC),
            )

            // Assert
            assertEquals(1, journalRepository.syncWithRemoteCallCount)
            assertEquals(1, habitRepository.syncWithRemoteCallCount)
        }

    @Test
    fun `should not sync local data when the screen loads while signed out`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedOut)

            // Act
            MainViewModel(
                observeJournalEntries = ObserveJournalEntriesUseCase(journalRepository),
                observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(journalRepository),
                observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(habitRepository),
                observeAuthState = ObserveAuthStateUseCase(authRepository),
                syncLocalData = SyncLocalDataUseCase(journalRepository, habitRepository),
                signOut = SignOutUseCase(authRepository, journalRepository, habitRepository, onboardingRepository),
                clock = Clock.fixed(Instant.now(), ZoneOffset.UTC),
            )

            // Assert
            assertEquals(0, journalRepository.syncWithRemoteCallCount)
            assertEquals(0, habitRepository.syncWithRemoteCallCount)
        }

    @Test
    fun `should show the account sheet when AccountIconClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(MainIntent.AccountIconClicked)

            // Assert
            assertTrue(viewModel.uiState.value.isAccountSheetVisible)
        }

    @Test
    fun `should hide the account sheet when AccountSheetDismissed is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(MainIntent.AccountIconClicked)

            // Act
            viewModel.onIntent(MainIntent.AccountSheetDismissed)

            // Assert
            assertTrue(!viewModel.uiState.value.isAccountSheetVisible)
        }

    @Test
    fun `should reflect a session that becomes signed in in authState`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedOut)
            val viewModel = viewModel(authRepository = authRepository)

            // Act
            authRepository.emit(AuthState.SignedIn(userId = "u1", email = "person@example.com"))

            // Assert
            assertEquals(AuthStateUi.SignedIn(email = "person@example.com"), viewModel.uiState.value.authState)
        }

    @Test
    fun `should close the sheet and emit NavigateToAccount when SignInSignUpPromptClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(MainIntent.AccountIconClicked)
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(MainIntent.SignInSignUpPromptClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(MainUiEvent.NavigateToAccount), events)
            assertTrue(!viewModel.uiState.value.isAccountSheetVisible)
            collectJob.cancel()
        }

    @Test
    fun `should show the sign-out confirmation when SignOutClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(MainIntent.SignOutClicked)

            // Assert
            assertTrue(viewModel.uiState.value.isSignOutConfirmVisible)
        }

    @Test
    fun `should hide the sign-out confirmation without signing out when SignOutCancelled is dispatched`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
            val viewModel = viewModel(authRepository = authRepository)
            viewModel.onIntent(MainIntent.SignOutClicked)

            // Act
            viewModel.onIntent(MainIntent.SignOutCancelled)

            // Assert
            assertTrue(!viewModel.uiState.value.isSignOutConfirmVisible)
            assertEquals(0, authRepository.signOutCallCount)
        }

    @Test
    fun `should sign out and close the dialog and sheet when SignOutConfirmed succeeds`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
            val viewModel = viewModel(authRepository = authRepository)
            viewModel.onIntent(MainIntent.AccountIconClicked)
            viewModel.onIntent(MainIntent.SignOutClicked)

            // Act
            viewModel.onIntent(MainIntent.SignOutConfirmed)
            runCurrent()

            // Assert
            val state = viewModel.uiState.value
            assertEquals(1, authRepository.signOutCallCount)
            assertTrue(!state.isSigningOut)
            assertTrue(!state.isSignOutConfirmVisible)
            assertTrue(!state.isAccountSheetVisible)
        }

    @Test
    fun `should stop signing out but leave the dialog and sheet open when SignOutConfirmed fails`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
            authRepository.signOutError = AuthError.Unknown
            val viewModel = viewModel(authRepository = authRepository)
            viewModel.onIntent(MainIntent.AccountIconClicked)
            viewModel.onIntent(MainIntent.SignOutClicked)

            // Act
            viewModel.onIntent(MainIntent.SignOutConfirmed)
            runCurrent()

            // Assert
            val state = viewModel.uiState.value
            assertEquals(1, authRepository.signOutCallCount)
            assertTrue(!state.isSigningOut)
            assertTrue(state.isSignOutConfirmVisible)
            assertTrue(state.isAccountSheetVisible)
        }

    @Test
    fun `should clear synced local data when SignOutConfirmed succeeds`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
            val viewModel = MainViewModel(
                observeJournalEntries = ObserveJournalEntriesUseCase(journalRepository),
                observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(journalRepository),
                observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(habitRepository),
                observeAuthState = ObserveAuthStateUseCase(authRepository),
                syncLocalData = SyncLocalDataUseCase(journalRepository, habitRepository),
                signOut = SignOutUseCase(authRepository, journalRepository, habitRepository, onboardingRepository),
                clock = Clock.fixed(Instant.now(), ZoneOffset.UTC),
            )
            viewModel.onIntent(MainIntent.AccountIconClicked)
            viewModel.onIntent(MainIntent.SignOutClicked)

            // Act
            viewModel.onIntent(MainIntent.SignOutConfirmed)
            runCurrent()

            // Assert
            assertEquals(1, journalRepository.clearLocalCallCount)
            assertEquals(1, habitRepository.clearLocalCallCount)
            assertEquals(1, onboardingRepository.resetSyncFlagCallCount)
        }

    @Test
    fun `should not clear local data when SignOutConfirmed fails`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
            authRepository.signOutError = AuthError.Unknown
            val viewModel = MainViewModel(
                observeJournalEntries = ObserveJournalEntriesUseCase(journalRepository),
                observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(journalRepository),
                observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(habitRepository),
                observeAuthState = ObserveAuthStateUseCase(authRepository),
                syncLocalData = SyncLocalDataUseCase(journalRepository, habitRepository),
                signOut = SignOutUseCase(authRepository, journalRepository, habitRepository, onboardingRepository),
                clock = Clock.fixed(Instant.now(), ZoneOffset.UTC),
            )
            viewModel.onIntent(MainIntent.AccountIconClicked)
            viewModel.onIntent(MainIntent.SignOutClicked)

            // Act
            viewModel.onIntent(MainIntent.SignOutConfirmed)
            runCurrent()

            // Assert
            assertEquals(0, journalRepository.clearLocalCallCount)
            assertEquals(0, habitRepository.clearLocalCallCount)
            assertEquals(0, onboardingRepository.resetSyncFlagCallCount)
        }
}
