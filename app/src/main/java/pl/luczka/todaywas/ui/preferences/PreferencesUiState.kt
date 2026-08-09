package pl.luczka.todaywas.ui.preferences

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.AuthStateUi

@Immutable
data class PreferencesUiState(
    val authState: AuthStateUi,
)
