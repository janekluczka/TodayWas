package pl.luczka.todaywas.ui.habit

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.HabitTypeUiState

@Immutable
data class CreateHabitUiState(
    val name: String,
    val description: String,
    val type: HabitTypeUiState,
    val scaleMin: String,
    val scaleMax: String,
    val isSaving: Boolean,
    val saveError: Boolean,
)
