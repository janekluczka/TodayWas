package pl.luczka.todaywas.feature.main.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.core.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.core.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.core.ui.mapper.toSortedHabitUiStates
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.core.ui.model.AuthErrorUiState
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.core.ui.model.HabitSortUiState
import pl.luczka.todaywas.core.ui.model.HabitUiState
import pl.luczka.todaywas.core.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.domain.auth.model.AuthError
import pl.luczka.todaywas.domain.auth.model.AuthException
import pl.luczka.todaywas.domain.auth.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.journal.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.journal.usecase.ObserveJournalEntriesUseCase

@HiltViewModel
internal class MainViewModel @Inject constructor(
    observeJournalEntries: ObserveJournalEntriesUseCase,
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    observeAuthState: ObserveAuthStateUseCase,
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

    val uiState: StateFlow<MainUiState> = combine(
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
            disabledFabActions = toDisabledFabActions(addableSlots.map { it.toUiState() }, habits),
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
            disabledFabActions = setOf(FabActionUiState.ADD_JOURNAL_ENTRY, FabActionUiState.LOG_HABIT_CHECK_INS),
            fabExpanded = false,
            authState = AuthStateUi.Loading,
            isAccountSheetVisible = false,
            isSignOutConfirmVisible = false,
            isSigningOut = false,
        ),
    )

    fun onIntent(intent: MainIntent) {
        when (intent) {
            MainIntent.FabActionClicked -> onFabActionClicked()
            MainIntent.FabToggled -> onFabToggled()
            MainIntent.AccountIconClicked -> onAccountIconClicked()
            MainIntent.AccountSheetDismissed -> onAccountSheetDismissed()
            MainIntent.SignInSignUpPromptClicked -> onSignInSignUpPromptClicked()
            MainIntent.SignOutClicked -> onSignOutClicked()
            MainIntent.SignOutConfirmed -> onSignOutConfirmed()
            MainIntent.SignOutCancelled -> onSignOutCancelled()
            MainIntent.UserMessageShown -> onUserMessageShown()
        }
    }

    private fun onFabActionClicked() {
        screenState.update { it.copy(fabExpanded = false) }
    }

    private fun onFabToggled() {
        screenState.update { it.copy(fabExpanded = !it.fabExpanded) }
    }

    private fun onAccountIconClicked() {
        screenState.update { it.copy(isAccountSheetVisible = true) }
    }

    private fun onAccountSheetDismissed() {
        screenState.update { it.copy(isAccountSheetVisible = false) }
    }

    private fun onSignInSignUpPromptClicked() {
        screenState.update { it.copy(isAccountSheetVisible = false) }
    }

    private fun onSignOutClicked() {
        screenState.update { it.copy(isSignOutConfirmVisible = true) }
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
                    it.copy(
                        isSigningOut = false,
                        userMessage = error.toUiState(),
                    )
                }
            }
        }
    }

    private fun onSignOutCancelled() {
        screenState.update { it.copy(isSignOutConfirmVisible = false) }
    }

    private fun onUserMessageShown() {
        screenState.update { it.copy(userMessage = null) }
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
