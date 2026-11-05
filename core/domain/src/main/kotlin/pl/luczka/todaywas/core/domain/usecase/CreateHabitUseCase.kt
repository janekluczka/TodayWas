package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.core.domain.model.HabitType
import pl.luczka.todaywas.core.domain.repository.HabitRepository

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
