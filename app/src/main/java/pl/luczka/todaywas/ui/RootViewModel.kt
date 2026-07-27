package pl.luczka.todaywas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    observeOnboardingState: ObserveOnboardingStateUseCase,
) : ViewModel() {

    private val _initialDestination = MutableStateFlow<TodayWasKey?>(null)
    val initialDestination: StateFlow<TodayWasKey?> = _initialDestination.asStateFlow()

    // Guards the one-time routing decision — a focus confirmed mid-onboarding flips the
    // persisted `completed` flag before the Account/All-set steps are shown, so later
    // emissions must not re-trigger routing (see OnboardingRepositoryImpl.saveFocus()).
    private var initialized = false

    init {
        viewModelScope.launch {
            observeOnboardingState().collect { state ->
                if (!initialized) {
                    initialized = true
                    _initialDestination.value = if (state.completed) MainKey else OnboardingKey
                }
            }
        }
    }
}
