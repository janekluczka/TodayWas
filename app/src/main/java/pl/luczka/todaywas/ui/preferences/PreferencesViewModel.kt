package pl.luczka.todaywas.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.toUiState
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val observeAuthState: ObserveAuthStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState(authState = AuthStateUi.Loading))
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<PreferencesUiEvent>(Channel.BUFFERED)
    val events: Flow<PreferencesUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeAuthState().collect { state ->
                _uiState.update { it.copy(authState = state.toUiState()) }
            }
        }
    }

    fun onIntent(intent: PreferencesIntent) {
        when (intent) {
            PreferencesIntent.AccountCardClicked -> eventChannel.trySend(PreferencesUiEvent.NavigateToAccount)
        }
    }
}
