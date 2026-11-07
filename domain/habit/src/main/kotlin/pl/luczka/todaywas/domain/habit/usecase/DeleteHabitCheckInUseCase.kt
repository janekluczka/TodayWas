package pl.luczka.todaywas.domain.habit.usecase

import java.time.LocalDate
import javax.inject.Inject
import pl.luczka.todaywas.domain.habit.repository.HabitRepository

class DeleteHabitCheckInUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(
        habitId: String,
        date: LocalDate,
    ): Result<Unit> = repository.deleteCheckIn(habitId, date)
}
