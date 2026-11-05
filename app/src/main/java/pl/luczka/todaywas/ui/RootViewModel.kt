package pl.luczka.todaywas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import pl.luczka.todaywas.core.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.feature.main.api.navigation.MainKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingWelcomeKey

@HiltViewModel
class RootViewModel @Inject constructor(
    observeOnboardingState: ObserveOnboardingStateUseCase,
) : ViewModel() {

    // A one-time routing decision: onboarding flips the persisted `completed` flag when the user
    // reaches All-set (or taps Skip from any step), so only the first emission may route (see
    // OnboardingRepositoryImpl.completeOnboarding()). Lazily rather than WhileSubscribed: a
    // restarted upstream would take a later emission and re-route.
    val initialDestination: StateFlow<NavKey?> = observeOnboardingState()
        .take(1)
        .map { state -> if (state.completed) MainKey else OnboardingWelcomeKey }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null,
        )
}
