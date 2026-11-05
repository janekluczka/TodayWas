package pl.luczka.todaywas.feature.onboarding.impl.allset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    // State owned by this screen itself, as opposed to the auth state and timer it observes.
    private data class ScreenState(
        val isGetStartedClicked: Boolean = false,
        val isNavigationHandled: Boolean = false,
    )

    private val screenState = MutableStateFlow(ScreenState())

    // Part of the state pipeline, so it only runs while the screen is collected.
    private val autoAdvanceElapsed = flow {
        emit(false)
        delay(AUTO_ADVANCE_DELAY_MS)
        emit(true)
    }

    // Finishes once, whichever of the timer and a tap comes first.
    val uiState: StateFlow<OnboardingAllSetUiState> = combine(
        observeAuthState(),
        autoAdvanceElapsed,
        screenState,
    ) { authState, elapsed, screen ->
        val isFinished = screen.isGetStartedClicked || elapsed
        OnboardingAllSetUiState(
            reason = reason,
            email = (authState.toUiState() as? AuthStateUi.SignedIn)?.email,
            pendingNavigation = OnboardingAllSetNavigation.Finished.takeIf {
                isFinished && !screen.isNavigationHandled
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingAllSetUiState(reason = reason),
    )

    fun onIntent(intent: OnboardingAllSetIntent) {
        when (intent) {
            OnboardingAllSetIntent.GetStartedClicked -> onGetStartedClicked()
            OnboardingAllSetIntent.NavigationHandled -> onNavigationHandled()
        }
    }

    private fun onGetStartedClicked() {
        screenState.update { it.copy(isGetStartedClicked = true) }
    }

    private fun onNavigationHandled() {
        screenState.update { it.copy(isNavigationHandled = true) }
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted reason: AllSetReason): OnboardingAllSetViewModel
    }
}
