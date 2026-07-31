package pl.luczka.todaywas.ui.habit

import pl.luczka.todaywas.ui.model.HabitTypeUiState

sealed interface CreateHabitIntent {

    data class NameChanged(
        val name: String,
    ) : CreateHabitIntent

    data class DescriptionChanged(
        val description: String,
    ) : CreateHabitIntent

    data class TypeChanged(
        val type: HabitTypeUiState,
    ) : CreateHabitIntent

    data class ScaleMinChanged(
        val scaleMin: String,
    ) : CreateHabitIntent

    data class ScaleMaxChanged(
        val scaleMax: String,
    ) : CreateHabitIntent

    data object SaveClicked : CreateHabitIntent

    data object CancelClicked : CreateHabitIntent
}
