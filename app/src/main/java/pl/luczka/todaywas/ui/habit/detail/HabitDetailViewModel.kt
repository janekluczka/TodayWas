package pl.luczka.todaywas.ui.habit.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.availableWindows
import pl.luczka.todaywas.domain.usecase.DeleteHabitCheckInUseCase
import pl.luczka.todaywas.domain.usecase.DeleteHabitUseCase
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.UpdateHabitCheckInUseCase
import pl.luczka.todaywas.domain.util.HabitContributionCalculator
import pl.luczka.todaywas.ui.mapper.toDomain
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

private data class HabitDetailViewModelState(
    val isLoading: Boolean = true,
    val habitName: String = "",
    val type: HabitTypeUiState = HabitTypeUiState.BINARY,
    val range: IntRange = 0..0,
    val checkIns: List<HabitCheckIn> = emptyList(),
    val pendingValues: Map<LocalDate, Int> = emptyMap(),
    val selectedWindow: ContributionWindow = ContributionWindow.RollingTwelveMonths,
    val isEditSheetOpen: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
    val saveErrorIsWindowExpired: Boolean = false,
    val isDeleteHabitDialogVisible: Boolean = false,
    val isDeletingHabit: Boolean = false,
    val deleteHabitError: Boolean = false,
    val checkInPendingDelete: LocalDate? = null,
    val isDeletingCheckIn: Boolean = false,
    val deleteCheckInError: Boolean = false,
) {
    fun toUiState(
        now: Instant,
        contributionGrid: ContributionGridUiState,
        availableWindows: List<ContributionWindowUiState>,
    ): HabitDetailUiState = HabitDetailUiState(
        isLoading = isLoading,
        habitName = habitName,
        type = type,
        range = range,
        rows = checkIns.toHabitDetailRows(pendingValues, now),
        contributionGrid = contributionGrid,
        availableWindows = availableWindows,
        selectedWindow = selectedWindow.toUiState(),
        isEditSheetOpen = isEditSheetOpen,
        isSaving = isSaving,
        saveError = saveError,
        saveErrorIsWindowExpired = saveErrorIsWindowExpired,
        isDeleteHabitDialogVisible = isDeleteHabitDialogVisible,
        isDeletingHabit = isDeletingHabit,
        deleteHabitError = deleteHabitError,
        checkInPendingDelete = checkInPendingDelete,
        isDeletingCheckIn = isDeletingCheckIn,
        deleteCheckInError = deleteCheckInError,
    )
}

private data class ContributionData(
    val grid: ContributionGridUiState,
    val availableWindows: List<ContributionWindowUiState>,
)

@HiltViewModel(assistedFactory = HabitDetailViewModel.Factory::class)
class HabitDetailViewModel @AssistedInject constructor(
    @Assisted private val habitId: String,
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val logHabitCheckIns: LogHabitCheckInsUseCase,
    private val updateHabitCheckIn: UpdateHabitCheckInUseCase,
    private val deleteHabit: DeleteHabitUseCase,
    private val deleteHabitCheckIn: DeleteHabitCheckInUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val viewModelState = MutableStateFlow(HabitDetailViewModelState())

    // Only recomputes when checkIns/selectedWindow actually change — not on every unrelated
    // state change (pendingValues, isSaving, isEditSheetOpen) during an edit-sheet session, which
    // `toUiState` being called on every viewModelState emission would otherwise trigger.
    private val contributionData: Flow<ContributionData> = viewModelState
        .map { it.checkIns to it.selectedWindow }
        .distinctUntilChanged()
        .map { (checkIns, window) ->
            val now = clock.instant()
            ContributionData(
                grid = HabitContributionCalculator.compute(checkIns, window, now).toUiState(now),
                availableWindows = availableWindows(checkIns.minOfOrNull { it.date }, now).map { it.toUiState() },
            )
        }

    val uiState: StateFlow<HabitDetailUiState> = combine(viewModelState, contributionData) { state, contribution ->
        state.toUiState(clock.instant(), contribution.grid, contribution.availableWindows)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = run {
            val now = clock.instant()
            val initialState = HabitDetailViewModelState()
            initialState.toUiState(
                now = now,
                contributionGrid = HabitContributionCalculator.compute(emptyList(), initialState.selectedWindow, now).toUiState(now),
                availableWindows = availableWindows(null, now).map { it.toUiState() },
            )
        },
    )

    private val eventChannel = Channel<HabitDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<HabitDetailUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeHabitCheckInBoard().collect { board ->
                val habit = board.habits.find { it.id == habitId } ?: return@collect
                val checkIns = board.checkIns.filter { it.habitId == habitId }
                viewModelState.update {
                    it.copy(
                        isLoading = false,
                        habitName = habit.name,
                        type = habit.type.toUiState(),
                        range = habit.detailRange(),
                        checkIns = checkIns,
                    )
                }
            }
        }
    }

    fun onIntent(intent: HabitDetailIntent) {
        when (intent) {
            HabitDetailIntent.EditClicked -> onEditClicked()
            is HabitDetailIntent.ValueChanged -> onValueChanged(intent.date, intent.value)
            HabitDetailIntent.SaveClicked -> onSaveClicked()
            HabitDetailIntent.CancelEditClicked -> onCancelEditClicked()
            HabitDetailIntent.BackClicked -> onBackClicked()
            is HabitDetailIntent.WindowSelected -> onWindowSelected(intent.window)
            HabitDetailIntent.DeleteHabitClicked -> onDeleteHabitClicked()
            HabitDetailIntent.DeleteHabitConfirmed -> onDeleteHabitConfirmed()
            HabitDetailIntent.DeleteHabitDismissed -> onDeleteHabitDismissed()
            is HabitDetailIntent.DeleteCheckInClicked -> onDeleteCheckInClicked(intent.date)
            HabitDetailIntent.DeleteCheckInConfirmed -> onDeleteCheckInConfirmed()
            HabitDetailIntent.DeleteCheckInDismissed -> onDeleteCheckInDismissed()
        }
    }

    private fun onEditClicked() {
        viewModelState.update { it.copy(isEditSheetOpen = true) }
    }

    private fun onWindowSelected(window: ContributionWindowUiState) {
        viewModelState.update { it.copy(selectedWindow = window.toDomain()) }
    }

    private fun onValueChanged(
        date: LocalDate,
        value: Int?,
    ) {
        viewModelState.update { state ->
            state.copy(
                pendingValues = if (value == null) {
                    state.pendingValues - date
                } else {
                    state.pendingValues + (date to value)
                },
            )
        }
    }

    private fun onCancelEditClicked() {
        viewModelState.update { it.copy(isEditSheetOpen = false, pendingValues = emptyMap()) }
    }

    private fun onBackClicked() {
        eventChannel.trySend(HabitDetailUiEvent.NavigatedBack)
    }

    private fun onSaveClicked() {
        val state = viewModelState.value
        if (state.isSaving) return
        val pending = state.pendingValues
        if (pending.isEmpty()) {
            viewModelState.update { it.copy(isEditSheetOpen = false) }
            return
        }
        val checkInsByDate = state.checkIns.associateBy { it.date }
        viewModelScope.launch {
            viewModelState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                    saveErrorIsWindowExpired = false,
                )
            }
            val results = pending.map { (date, value) ->
                val existing = checkInsByDate[date]
                if (existing != null) {
                    updateHabitCheckIn(habitId, date, value, existing.createdAt)
                } else {
                    logHabitCheckIns(date, mapOf(habitId to value))
                }
            }
            if (results.all { it.isSuccess }) {
                viewModelState.update {
                    it.copy(
                        isSaving = false,
                        isEditSheetOpen = false,
                        pendingValues = emptyMap(),
                    )
                }
            } else {
                val windowExpired = results.any { it.exceptionOrNull() is EditWindowExpiredException }
                viewModelState.update {
                    it.copy(
                        isSaving = false,
                        saveError = true,
                        saveErrorIsWindowExpired = windowExpired,
                    )
                }
            }
        }
    }

    private fun onDeleteHabitClicked() {
        viewModelState.update { it.copy(isDeleteHabitDialogVisible = true) }
    }

    private fun onDeleteHabitDismissed() {
        viewModelState.update { it.copy(isDeleteHabitDialogVisible = false) }
    }

    private fun onDeleteHabitConfirmed() {
        if (viewModelState.value.isDeletingHabit) return
        viewModelScope.launch {
            viewModelState.update { it.copy(isDeletingHabit = true, deleteHabitError = false) }
            val result = deleteHabit(habitId)
            if (result.isSuccess) {
                eventChannel.trySend(HabitDetailUiEvent.NavigatedBack)
            } else {
                viewModelState.update {
                    it.copy(isDeletingHabit = false, deleteHabitError = true, isDeleteHabitDialogVisible = false)
                }
            }
        }
    }

    private fun onDeleteCheckInClicked(date: LocalDate) {
        viewModelState.update { it.copy(checkInPendingDelete = date) }
    }

    private fun onDeleteCheckInDismissed() {
        viewModelState.update { it.copy(checkInPendingDelete = null) }
    }

    private fun onDeleteCheckInConfirmed() {
        val state = viewModelState.value
        val date = state.checkInPendingDelete ?: return
        if (state.isDeletingCheckIn) return
        viewModelScope.launch {
            viewModelState.update { it.copy(isDeletingCheckIn = true, deleteCheckInError = false) }
            val result = deleteHabitCheckIn(habitId, date)
            viewModelState.update {
                it.copy(
                    isDeletingCheckIn = false,
                    deleteCheckInError = result.isFailure,
                    checkInPendingDelete = null,
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted habitId: String,
        ): HabitDetailViewModel
    }
}
