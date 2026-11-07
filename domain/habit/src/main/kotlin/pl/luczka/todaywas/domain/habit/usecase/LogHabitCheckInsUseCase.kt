package pl.luczka.todaywas.domain.habit.usecase

import java.time.LocalDate
import javax.inject.Inject
import pl.luczka.todaywas.domain.habit.repository.HabitRepository

class LogHabitCheckInsUseCase @Inject constructor(
    private val repository: HabitRepository,
) {

    suspend operator fun invoke(
        date: LocalDate,
        values: Map<String, Int>,
    ): Result<Unit> = repository.addCheckIns(date, values)
}
