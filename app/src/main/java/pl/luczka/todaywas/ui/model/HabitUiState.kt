package pl.luczka.todaywas.ui.model

import androidx.compose.runtime.Immutable

enum class HabitTypeUiState {
    BINARY,
    SCALE,
}

sealed interface HabitCheckInStatusUiState {

    data object NotLogged : HabitCheckInStatusUiState

    data class LoggedBinary(
        val done: Boolean,
    ) : HabitCheckInStatusUiState

    data class LoggedScale(
        val value: Int,
    ) : HabitCheckInStatusUiState
}

@Immutable
data class HabitUiState(
    val id: Long,
    val name: String,
    val type: HabitTypeUiState,
    val todayStatus: HabitCheckInStatusUiState,
)
