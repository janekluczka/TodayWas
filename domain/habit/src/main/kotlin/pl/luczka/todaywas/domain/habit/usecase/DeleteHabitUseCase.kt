package pl.luczka.todaywas.domain.habit.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.habit.repository.HabitRepository

class DeleteHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteHabit(id)
}
