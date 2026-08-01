package pl.luczka.todaywas.ui.habit

import androidx.compose.runtime.Immutable

val HabitScaleStepsRange = 2..7

@Immutable
data class CreateHabitUiState(
    val name: String,
    val description: String,
    val scaleSteps: Int,
    val isSaving: Boolean,
    val saveError: Boolean,
) {
    // A 2-step habit is a plain yes/no; more steps make it a scale. There's no separate
    // "type" the user picks — the step count alone decides it.
    val isBinary: Boolean get() = scaleSteps == HabitScaleStepsRange.first
}
