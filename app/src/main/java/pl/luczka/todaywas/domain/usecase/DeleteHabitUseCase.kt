package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.repository.HabitRepository
import javax.inject.Inject

class DeleteHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteHabit(id)
}
