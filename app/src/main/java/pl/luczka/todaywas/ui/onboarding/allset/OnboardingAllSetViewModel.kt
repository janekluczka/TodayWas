package pl.luczka.todaywas.ui.onboarding.allset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.onboarding.AllSetReason

private const val AUTO_ADVANCE_DELAY_MS = 5_000L

@HiltViewModel(assistedFactory = OnboardingAllSetViewModel.Factory::class)
class OnboardingAllSetViewModel @AssistedInject constructor(
    @Assisted reason: AllSetReason,
    observeAuthState: ObserveAuthStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingAllSetUiState(reason = reason))
    val uiState: StateFlow<OnboardingAllSetUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<OnboardingAllSetUiEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingAllSetUiEvent> = eventChannel.receiveAsFlow()

    private var finished = false

    init {
        viewModelScope.launch {
            observeAuthState().collect { state ->
                val email = (state.toUiState() as? AuthStateUi.SignedIn)?.email
                _uiState.update { it.copy(email = email) }
            }
        }
        viewModelScope.launch {
            delay(AUTO_ADVANCE_DELAY_MS)
            finish()
        }
    }

    fun onIntent(intent: OnboardingAllSetIntent) {
        when (intent) {
            OnboardingAllSetIntent.GetStartedClicked -> finish()
        }
    }

    // Guards against both the auto-advance timer and a manual tap firing Finished twice.
    private fun finish() {
        if (finished) return
        finished = true
        eventChannel.trySend(OnboardingAllSetUiEvent.Finished)
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted reason: AllSetReason,
        ): OnboardingAllSetViewModel
    }
}
