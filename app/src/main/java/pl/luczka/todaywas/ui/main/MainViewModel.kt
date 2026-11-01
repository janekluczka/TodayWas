package pl.luczka.todaywas.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthException
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.ui.mapper.toSortedHabitUiStates
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitSortUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeJournalEntries: ObserveJournalEntriesUseCase,
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val syncLocalData: SyncLocalDataUseCase,
    private val signOut: SignOutUseCase,
    private val clock: Clock,
) : ViewModel() {

    // State owned by this screen itself, as opposed to the data streams it observes.
    private data class ScreenState(
        val fabExpanded: Boolean = false,
        val isAccountSheetVisible: Boolean = false,
        val isSignOutConfirmVisible: Boolean = false,
        val isSigningOut: Boolean = false,
        val userMessage: AuthErrorUiState? = null,
    )

    private val screenState = MutableStateFlow(ScreenState())

    val uiState: StateFlow<MainUiState> =
        combine(
            observeJournalEntries(),
            observeAddableJournalDateSlots(),
            observeHabitCheckInBoard(),
            observeAuthState(),
            screenState,
        ) { entries, addableSlots, board, authState, screen ->
            val habits = board.toSortedHabitUiStates(
                today = LocalDate.now(clock),
                sort = HabitSortUiState.RECENTLY_CHECKED_IN,
            )
            MainUiState(
                isLoading = false,
                journalEntries = entries.map { it.toUiState() },
                habits = habits,
                disabledFabActions = toDisabledFabActions(
                    addableSlots.map { it.toUiState() },
                    habits,
                ),
                fabExpanded = screen.fabExpanded,
                authState = authState.toUiState(),
                isAccountSheetVisible = screen.isAccountSheetVisible,
                isSignOutConfirmVisible = screen.isSignOutConfirmVisible,
                isSigningOut = screen.isSigningOut,
                userMessage = screen.userMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(
                isLoading = true,
                journalEntries = emptyList(),
                habits = emptyList(),
                disabledFabActions = setOf(
                    FabActionUiState.ADD_JOURNAL_ENTRY,
                    FabActionUiState.LOG_HABIT_CHECK_INS,
                ),
                fabExpanded = false,
                authState = AuthStateUi.Loading,
                isAccountSheetVisible = false,
                isSignOutConfirmVisible = false,
                isSigningOut = false,
            ),
        )

    init {
        // Basic reinstall/new-device restore: silently refresh local data from remote once, when
        // the Main screen itself loads while signed in — Room stays the read source, so uiState's
        // observed flows pick up whatever lands locally without any extra wiring.
        viewModelScope.launch {
            if (observeAuthState().first { it !is AuthState.Loading } is AuthState.SignedIn) {
                syncLocalData()
            }
        }
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            MainIntent.FabActionClicked -> screenState.update { it.copy(fabExpanded = false) }
            MainIntent.FabToggled -> screenState.update { it.copy(fabExpanded = !it.fabExpanded) }
            MainIntent.AccountIconClicked ->
                screenState.update { it.copy(isAccountSheetVisible = true) }
            MainIntent.AccountSheetDismissed ->
                screenState.update { it.copy(isAccountSheetVisible = false) }
            MainIntent.SignInSignUpPromptClicked ->
                screenState.update { it.copy(isAccountSheetVisible = false) }
            MainIntent.SignOutClicked ->
                screenState.update { it.copy(isSignOutConfirmVisible = true) }
            MainIntent.SignOutConfirmed -> onSignOutConfirmed()
            MainIntent.SignOutCancelled ->
                screenState.update { it.copy(isSignOutConfirmVisible = false) }
            MainIntent.UserMessageShown -> screenState.update { it.copy(userMessage = null) }
        }
    }

    private fun onSignOutConfirmed() {
        if (screenState.value.isSigningOut) return
        viewModelScope.launch {
            screenState.update { it.copy(isSigningOut = true) }
            val result = signOut()
            if (result.isSuccess) {
                screenState.update {
                    it.copy(
                        isSigningOut = false,
                        isSignOutConfirmVisible = false,
                        isAccountSheetVisible = false,
                    )
                }
            } else {
                val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
                screenState.update {
                    it.copy(isSigningOut = false, userMessage = error.toUiState())
                }
            }
        }
    }

    // CREATE_HABIT is never disabled — it has no eligibility criteria, unlike the other two.
    private fun toDisabledFabActions(
        addableSlots: List<JournalDateSlotUiState>,
        habits: List<HabitUiState>,
    ): Set<FabActionUiState> = setOfNotNull(
        FabActionUiState.ADD_JOURNAL_ENTRY.takeIf { addableSlots.isEmpty() },
        FabActionUiState.LOG_HABIT_CHECK_INS.takeIf { habits.isEmpty() },
    )
}
