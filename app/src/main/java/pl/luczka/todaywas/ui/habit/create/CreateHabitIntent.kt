package pl.luczka.todaywas.ui.habit.create

sealed interface CreateHabitIntent {

    data class NameChanged(
        val name: String,
    ) : CreateHabitIntent

    data class DescriptionChanged(
        val description: String,
    ) : CreateHabitIntent

    data class ScaleStepsChanged(
        val steps: Int,
    ) : CreateHabitIntent

    data object SaveClicked : CreateHabitIntent

    data object CancelClicked : CreateHabitIntent
}
