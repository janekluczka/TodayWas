package pl.luczka.todaywas.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthException
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.model.ContributionGrid
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalContributionUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.ui.mapper.toDomain
import pl.luczka.todaywas.ui.mapper.toSortedHabitUiStates
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitSortUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeJournalEntries: ObserveJournalEntriesUseCase,
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    observeJournalContribution: ObserveJournalContributionUseCase,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val syncLocalData: SyncLocalDataUseCase,
    private val signOut: SignOutUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val selectedJournalWindow =
        MutableStateFlow<ContributionWindow>(ContributionWindow.RollingTwelveMonths)

    private val _uiState = MutableStateFlow(
        MainUiState(
            isLoading = true,
            journalEntries = emptyList(),
            habits = emptyList(),
            journalContributionGrid = ContributionGrid(
                window = ContributionWindow.RollingTwelveMonths,
                days = emptyMap(),
            ).toUiState(clock.instant()),
            journalAvailableWindows = listOf(ContributionWindowUiState.RollingTwelveMonths),
            journalSelectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            fabActions = emptyList(),
            fabExpanded = false,
            authState = AuthStateUi.Loading,
            isAccountSheetVisible = false,
            isSignOutConfirmVisible = false,
            isSigningOut = false,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MainUiEvent>(Channel.BUFFERED)
    val events: Flow<MainUiEvent> = eventChannel.receiveAsFlow()

    private val journalContributionData: Flow<JournalContributionData> = observeJournalContribution(
        selectedJournalWindow,
    ).map { summary ->
        val now = clock.instant()
        JournalContributionData(
            grid = summary.grid.toUiState(now),
            availableWindows = summary.availableWindows.map { it.toUiState() },
        )
    }

    init {
        viewModelScope.launch {
            val rawSources = combine(
                observeJournalEntries(),
                observeAddableJournalDateSlots(),
                observeHabitCheckInBoard(),
            ) { entries, addableSlots, board ->
                RawMainSources(
                    journalEntries = entries.map { it.toUiState() },
                    addableSlots = addableSlots.map { it.toUiState() },
                    habits = board.toSortedHabitUiStates(
                        today = LocalDate.now(clock),
                        sort = HabitSortUiState.RECENTLY_CHECKED_IN,
                    ),
                )
            }
            combine(
                rawSources,
                selectedJournalWindow,
                journalContributionData,
            ) { raw, selectedWindow, contribution ->
                CombinedMainState(
                    journalEntries = raw.journalEntries,
                    addableSlots = raw.addableSlots,
                    habits = raw.habits,
                    journalContributionGrid = contribution.grid,
                    journalAvailableWindows = contribution.availableWindows,
                    journalSelectedWindow = selectedWindow.toUiState(),
                )
            }.collect { combined ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        journalEntries = combined.journalEntries,
                        habits = combined.habits,
                        journalContributionGrid = combined.journalContributionGrid,
                        journalAvailableWindows = combined.journalAvailableWindows,
                        journalSelectedWindow = combined.journalSelectedWindow,
                        fabActions = toFabActions(combined.addableSlots, combined.habits),
                    )
                }
            }
        }
        // Basic reinstall/new-device restore: silently refresh local data from remote once, when
        // the Main screen itself loads while signed in — Room stays the read source, so this
        // screen's own observeJournalEntries()/observeHabitCheckInBoard() flows above pick up
        // whatever lands locally without any extra wiring.
        viewModelScope.launch {
            if (observeAuthState().first { it !is AuthState.Loading } is AuthState.SignedIn) {
                syncLocalData()
            }
        }
        // Feeds the account bottom sheet live — a continuous collect, unlike the one-shot first{}
        // above, so sign-out (or a session expiring) updates the open sheet in place.
        viewModelScope.launch {
            observeAuthState().collect { state ->
                _uiState.update { it.copy(authState = state.toUiState()) }
            }
        }
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.FabActionClicked -> onFabActionClicked(intent.action)
            MainIntent.FabToggled -> onFabToggled()
            is MainIntent.JournalEntryClicked -> onJournalEntryClicked(intent.entry)
            is MainIntent.HabitClicked -> onHabitClicked(intent.habit)
            is MainIntent.JournalWindowSelected -> onJournalWindowSelected(intent.window)
            MainIntent.JournalViewAllClicked -> eventChannel.trySend(
                MainUiEvent.NavigateToJournalList,
            )
            MainIntent.HabitViewAllClicked -> eventChannel.trySend(MainUiEvent.NavigateToHabitList)
            MainIntent.AccountIconClicked -> _uiState.update {
                it.copy(
                    isAccountSheetVisible = true,
                )
            }
            MainIntent.AccountSheetDismissed -> _uiState.update {
                it.copy(
                    isAccountSheetVisible = false,
                )
            }
            MainIntent.SignInSignUpPromptClicked -> onSignInSignUpPromptClicked()
            MainIntent.SignOutClicked -> _uiState.update { it.copy(isSignOutConfirmVisible = true) }
            MainIntent.SignOutConfirmed -> onSignOutConfirmed()
            MainIntent.SignOutCancelled -> _uiState.update {
                it.copy(
                    isSignOutConfirmVisible = false,
                )
            }
        }
    }

    private fun onSignInSignUpPromptClicked() {
        _uiState.update { it.copy(isAccountSheetVisible = false) }
        eventChannel.trySend(MainUiEvent.NavigateToAccount)
    }

    private fun onSignOutConfirmed() {
        if (_uiState.value.isSigningOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            val result = signOut()
            _uiState.update {
                it.copy(
                    isSigningOut = false,
                    isSignOutConfirmVisible =
                        if (result.isSuccess) false else it.isSignOutConfirmVisible,
                    isAccountSheetVisible =
                        if (result.isSuccess) false else it.isAccountSheetVisible,
                )
            }
            if (result.isFailure) {
                val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
                eventChannel.trySend(MainUiEvent.ShowError(error.toUiState()))
            }
        }
    }

    private fun onJournalWindowSelected(window: ContributionWindowUiState) {
        selectedJournalWindow.update { window.toDomain() }
    }

    private fun onFabActionClicked(action: FabActionUiState) {
        _uiState.update { it.copy(fabExpanded = false) }
        when (action) {
            FabActionUiState.ADD_JOURNAL_ENTRY -> eventChannel.trySend(
                MainUiEvent.NavigateToAddEntry,
            )
            FabActionUiState.CREATE_HABIT -> eventChannel.trySend(MainUiEvent.NavigateToCreateHabit)
            FabActionUiState.LOG_HABIT_CHECK_INS -> eventChannel.trySend(
                MainUiEvent.NavigateToLogHabitCheckIns,
            )
        }
    }

    private fun onFabToggled() {
        _uiState.update { it.copy(fabExpanded = !it.fabExpanded) }
    }

    private fun onJournalEntryClicked(entry: JournalEntryUiState) {
        eventChannel.trySend(MainUiEvent.NavigateToJournalDetail(entry))
    }

    private fun onHabitClicked(habit: HabitUiState) {
        eventChannel.trySend(MainUiEvent.NavigateToHabitDetail(habit.id))
    }

    private fun toFabActions(
        addableSlots: List<JournalDateSlotUiState>,
        habits: List<HabitUiState>,
    ): List<FabActionUiState> = listOfNotNull(
        FabActionUiState.ADD_JOURNAL_ENTRY.takeIf { addableSlots.isNotEmpty() },
        FabActionUiState.CREATE_HABIT,
        FabActionUiState.LOG_HABIT_CHECK_INS.takeIf { habits.isNotEmpty() },
    )

    private data class RawMainSources(
        val journalEntries: List<JournalEntryUiState>,
        val addableSlots: List<JournalDateSlotUiState>,
        val habits: List<HabitUiState>,
    )

    private data class JournalContributionData(
        val grid: ContributionGridUiState,
        val availableWindows: List<ContributionWindowUiState>,
    )

    private data class CombinedMainState(
        val journalEntries: List<JournalEntryUiState>,
        val addableSlots: List<JournalDateSlotUiState>,
        val habits: List<HabitUiState>,
        val journalContributionGrid: ContributionGridUiState,
        val journalAvailableWindows: List<ContributionWindowUiState>,
        val journalSelectedWindow: ContributionWindowUiState,
    )
}
