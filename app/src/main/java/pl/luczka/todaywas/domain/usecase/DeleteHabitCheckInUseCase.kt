package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

class DeleteHabitCheckInUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(
        habitId: String,
        date: LocalDate,
    ): Result<Unit> = repository.deleteCheckIn(habitId, date)
}
