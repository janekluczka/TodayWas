package pl.luczka.todaywas.ui.habit

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import java.time.LocalDate

@Immutable
data class LogHabitCheckInsUiState(
    val selectableDates: List<LocalDate>,
    val selectedDate: LocalDate,
    val rows: List<HabitCheckInRowUiState>,
    val isSaving: Boolean,
    val saveError: Boolean,
)

sealed interface HabitCheckInRowUiState {

    val habitId: String
    val name: String

    // A binary habit is just a 0..1 range under the hood — one segmented-row component handles
    // both, rather than a separate toggle widget for binary and a stepper for scale. `range`/
    // `type` are shared by both variants so an already-logged habit renders the exact same
    // (disabled) segmented control as an editable one, instead of a plain text substitute.
    val range: IntRange
    val type: HabitTypeUiState

    data class Editable(
        override val habitId: String,
        override val name: String,
        override val range: IntRange,
        override val type: HabitTypeUiState,
        val value: Int?,
    ) : HabitCheckInRowUiState

    data class AlreadyLogged(
        override val habitId: String,
        override val name: String,
        override val range: IntRange,
        override val type: HabitTypeUiState,
        val value: Int,
    ) : HabitCheckInRowUiState
}
