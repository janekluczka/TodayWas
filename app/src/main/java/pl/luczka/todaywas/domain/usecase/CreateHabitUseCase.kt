package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.HabitRepository
import pl.luczka.todaywas.domain.model.HabitType
import javax.inject.Inject

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
