package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.repository.HabitRepository

class DeleteHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteHabit(id)
}
