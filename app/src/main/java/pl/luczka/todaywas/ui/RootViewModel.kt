package pl.luczka.todaywas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.luczka.todaywas.core.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.feature.main.api.navigation.MainKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingWelcomeKey

@HiltViewModel
class RootViewModel @Inject constructor(
    observeOnboardingState: ObserveOnboardingStateUseCase,
) : ViewModel() {

    private val _initialDestination = MutableStateFlow<NavKey?>(null)
    val initialDestination: StateFlow<NavKey?> = _initialDestination.asStateFlow()

    // Guards the one-time routing decision — onboarding flips the persisted `completed` flag
    // when the user reaches All-set (or taps Skip from any step), so later emissions must not
    // re-trigger routing (see OnboardingRepositoryImpl.completeOnboarding()).
    private var initialized = false

    init {
        viewModelScope.launch {
            observeOnboardingState().collect { state ->
                if (!initialized) {
                    initialized = true
                    _initialDestination.value = if (state.completed) MainKey else OnboardingWelcomeKey
                }
            }
        }
    }
}
