package pl.luczka.todaywas.ui.model

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel

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
    val id: String,
    val name: String,
    val type: HabitTypeUiState,
    val todayStatus: HabitCheckInStatusUiState,
    // Today's contribution level, computed with the same calculator every grid uses — lets a
    // caller color a cell (e.g. Habit List's value badge) without recomputing anything itself.
    val todayLevel: DsContributionLevel = DsContributionLevel.NONE,
)
