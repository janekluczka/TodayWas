package pl.luczka.todaywas.ui.habit

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
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

    val habitId: Long
    val name: String

    sealed interface Editable : HabitCheckInRowUiState {

        data class Binary(
            override val habitId: Long,
            override val name: String,
            val value: Boolean?,
        ) : Editable

        data class Scale(
            override val habitId: Long,
            override val name: String,
            val value: Int?,
            val range: IntRange,
        ) : Editable
    }

    data class AlreadyLogged(
        override val habitId: Long,
        override val name: String,
        val status: HabitCheckInStatusUiState,
    ) : HabitCheckInRowUiState
}
