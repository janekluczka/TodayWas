package pl.luczka.todaywas.feature.onboarding.impl.allset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.core.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.feature.onboarding.api.navigation.AllSetReason

private const val AUTO_ADVANCE_DELAY_MS = 5_000L

@HiltViewModel(assistedFactory = OnboardingAllSetViewModel.Factory::class)
internal class OnboardingAllSetViewModel @AssistedInject constructor(
    @Assisted reason: AllSetReason,
    observeAuthState: ObserveAuthStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingAllSetUiState(reason = reason))
    val uiState: StateFlow<OnboardingAllSetUiState> = _uiState.asStateFlow()

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
            OnboardingAllSetIntent.GetStartedClicked -> onGetStartedClicked()
            OnboardingAllSetIntent.NavigationHandled -> onNavigationHandled()
        }
    }

    private fun onGetStartedClicked() {
        finish()
    }

    private fun onNavigationHandled() {
        _uiState.update { it.copy(pendingNavigation = null) }
    }

    // Guards against both the auto-advance timer and a manual tap finishing twice.
    private fun finish() {
        if (finished) return
        finished = true
        _uiState.update { it.copy(pendingNavigation = OnboardingAllSetNavigation.Finished) }
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted reason: AllSetReason): OnboardingAllSetViewModel
    }
}
