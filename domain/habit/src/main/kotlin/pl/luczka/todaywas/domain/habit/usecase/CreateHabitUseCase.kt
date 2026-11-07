package pl.luczka.todaywas.domain.habit.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.habit.model.HabitType
import pl.luczka.todaywas.domain.habit.repository.HabitRepository

class CreateHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {

    suspend operator fun invoke(
        name: String,
        description: String?,
        type: HabitType,
        scaleMin: Int?,
        scaleMax: Int?,
    ): Result<Unit> = repository.createHabit(name, description, type, scaleMin, scaleMax)
}
