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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.JournalContributionCalculator
import pl.luczka.todaywas.domain.model.availableWindows
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.toDomain
import pl.luczka.todaywas.ui.model.toHabitUiStates
import pl.luczka.todaywas.ui.model.toUiState
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeJournalEntries: ObserveJournalEntriesUseCase,
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val syncLocalData: SyncLocalDataUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val selectedJournalWindow = MutableStateFlow<ContributionWindow>(ContributionWindow.RollingTwelveMonths)

    private val _uiState = MutableStateFlow(
        MainUiState(
            journalEntries = emptyList(),
            habits = emptyList(),
            journalContributionGrid = JournalContributionCalculator
                .compute(
                    emptyList(),
                    ContributionWindow.RollingTwelveMonths,
                    clock.instant(),
                ).toUiState(clock.instant()),
            journalAvailableWindows = listOf(ContributionWindowUiState.RollingTwelveMonths),
            journalSelectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            fabActions = emptyList(),
            fabExpanded = false,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MainUiEvent>(Channel.BUFFERED)
    val events: Flow<MainUiEvent> = eventChannel.receiveAsFlow()

    // Only recomputes when journal entries/selectedJournalWindow actually change — not on every
    // unrelated emission (onboarding, habit board) from the wider combine below, which computing
    // this inline in that single combine's lambda would otherwise trigger on every tick.
    private val journalContributionData: Flow<JournalContributionData> = combine(
        observeJournalEntries(),
        selectedJournalWindow,
    ) { entries, window -> entries to window }
        .distinctUntilChanged()
        .map { (entries, window) ->
            val now = clock.instant()
            JournalContributionData(
                grid = JournalContributionCalculator.compute(entries, window, now).toUiState(now),
                availableWindows = availableWindows(entries.minOfOrNull { it.date }, now).map { it.toUiState() },
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
                    habits = board.toHabitUiStates(),
                )
            }
            combine(rawSources, selectedJournalWindow, journalContributionData) { raw, selectedWindow, contribution ->
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
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.FabActionClicked -> onFabActionClicked(intent.action)
            MainIntent.FabToggled -> onFabToggled()
            is MainIntent.JournalEntryClicked -> onJournalEntryClicked(intent.entry)
            is MainIntent.HabitClicked -> onHabitClicked(intent.habit)
            is MainIntent.JournalWindowSelected -> onJournalWindowSelected(intent.window)
        }
    }

    private fun onJournalWindowSelected(window: ContributionWindowUiState) {
        selectedJournalWindow.update { window.toDomain() }
    }

    private fun onFabActionClicked(action: FabActionUiState) {
        _uiState.update { it.copy(fabExpanded = false) }
        when (action) {
            FabActionUiState.ADD_JOURNAL_ENTRY -> eventChannel.trySend(MainUiEvent.NavigateToAddEntry)
            FabActionUiState.CREATE_HABIT -> eventChannel.trySend(MainUiEvent.NavigateToCreateHabit)
            FabActionUiState.LOG_HABIT_CHECK_INS -> eventChannel.trySend(MainUiEvent.NavigateToLogHabitCheckIns)
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
